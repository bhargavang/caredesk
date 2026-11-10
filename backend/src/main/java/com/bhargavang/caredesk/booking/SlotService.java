package com.bhargavang.caredesk.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.ServiceOfferingRepository;
import com.bhargavang.caredesk.catalog.Staff;
import com.bhargavang.caredesk.catalog.StaffRepository;
import com.bhargavang.caredesk.catalog.WorkingHours;
import com.bhargavang.caredesk.catalog.WorkingHoursRepository;
import com.bhargavang.caredesk.common.BusinessRuleException;
import com.bhargavang.caredesk.common.NotFoundException;
import com.bhargavang.caredesk.config.CareDeskProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out which start times are free. A start time is offered when the whole
 * appointment fits inside one of the staff member's working blocks, it isn't
 * too soon or too far ahead, and it doesn't overlap a live appointment.
 */
@Service
public class SlotService {

	private final ServiceOfferingRepository services;
	private final StaffRepository staff;
	private final WorkingHoursRepository workingHours;
	private final AppointmentRepository appointments;
	private final CareDeskProperties.Booking rules;
	private final Clock clock;

	public SlotService(ServiceOfferingRepository services, StaffRepository staff, WorkingHoursRepository workingHours,
			AppointmentRepository appointments, CareDeskProperties properties, Clock clock) {
		this.services = services;
		this.staff = staff;
		this.workingHours = workingHours;
		this.appointments = appointments;
		this.rules = properties.booking();
		this.clock = clock;
	}

	/**
	 * Free slots for a service on a date. With a staff id, only that person's slots.
	 * Without one ("anyone available"), one slot per start time, given to whoever
	 * has the fewest appointments that day.
	 */
	@Transactional(readOnly = true)
	public List<FreeSlot> findFreeSlots(Long serviceId, LocalDate date, Long staffId) {
		ServiceOffering service = services.findById(serviceId)
				.filter(ServiceOffering::isActive)
				.orElseThrow(() -> new NotFoundException("Service not found"));

		List<Staff> candidates;
		if (staffId != null) {
			Staff person = staff.findById(staffId)
					.filter(Staff::isActive)
					.orElseThrow(() -> new NotFoundException("Staff member not found"));
			if (!person.offers(service)) {
				throw new BusinessRuleException(person.getFullName() + " doesn't offer " + service.getName());
			}
			candidates = List.of(person);
		}
		else {
			candidates = staff.findActiveOfferingService(serviceId);
		}
		if (candidates.isEmpty() || !isWithinBookingWindow(date)) {
			return List.of();
		}

		List<Long> ids = candidates.stream().map(Staff::getId).toList();
		Map<Long, List<WorkingHours>> hoursByStaff = workingHours.findByStaffIdInAndDayOfWeek(ids, date.getDayOfWeek())
				.stream()
				.collect(Collectors.groupingBy(h -> h.getStaff().getId()));
		Map<Long, List<Appointment>> busyByStaff = appointments
				.findLiveOverlapping(ids, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
				.stream()
				.collect(Collectors.groupingBy(a -> a.getStaff().getId()));

		LocalDateTime earliest = earliestBookableStart();
		List<FreeSlot> free = new ArrayList<>();
		for (Staff person : candidates) {
			List<Appointment> busy = busyByStaff.getOrDefault(person.getId(), List.of());
			for (LocalDateTime start : candidateStarts(hoursByStaff.getOrDefault(person.getId(), List.of()), date,
					service.getDurationMinutes())) {
				LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
				if (!start.isBefore(earliest) && !overlapsAny(busy, start, end)) {
					free.add(new FreeSlot(start, end, person.getId(), person.getFullName()));
				}
			}
		}

		if (staffId == null) {
			free = onePerStartTime(free, busyByStaff);
		}
		free.sort(Comparator.comparing(FreeSlot::start).thenComparing(FreeSlot::staffId));
		return free;
	}

	/**
	 * True when {@code start} is a time this staff member would offer for the service,
	 * ignoring other bookings: inside working hours, on the slot grid, not too soon or too far ahead.
	 */
	public boolean isOfferedStart(Staff person, ServiceOffering service, LocalDateTime start) {
		LocalDate date = start.toLocalDate();
		if (!isWithinBookingWindow(date) || start.isBefore(earliestBookableStart())) {
			return false;
		}
		List<WorkingHours> hours = workingHours.findByStaffIdInAndDayOfWeek(List.of(person.getId()), date.getDayOfWeek());
		return candidateStarts(hours, date, service.getDurationMinutes()).contains(start);
	}

	List<LocalDateTime> candidateStarts(List<WorkingHours> hours, LocalDate date, int durationMinutes) {
		List<LocalDateTime> starts = new ArrayList<>();
		for (WorkingHours block : hours) {
			LocalDateTime blockEnd = date.atTime(block.getEndTime());
			for (LocalDateTime t = date.atTime(block.getStartTime()); !t.plusMinutes(durationMinutes).isAfter(blockEnd);
					t = t.plusMinutes(rules.slotStepMinutes())) {
				starts.add(t);
			}
		}
		return starts;
	}

	private boolean isWithinBookingWindow(LocalDate date) {
		LocalDate today = LocalDate.now(clock);
		return !date.isBefore(today) && !date.isAfter(today.plusDays(rules.maxDaysAhead()));
	}

	private LocalDateTime earliestBookableStart() {
		return LocalDateTime.now(clock).plusMinutes(rules.minLeadMinutes());
	}

	private static boolean overlapsAny(List<Appointment> busy, LocalDateTime start, LocalDateTime end) {
		return busy.stream().anyMatch(a -> a.getStartTime().isBefore(end) && a.getEndTime().isAfter(start));
	}

	private static List<FreeSlot> onePerStartTime(List<FreeSlot> slots, Map<Long, List<Appointment>> busyByStaff) {
		Comparator<FreeSlot> leastBusyFirst = Comparator
				.<FreeSlot>comparingInt(s -> busyByStaff.getOrDefault(s.staffId(), List.of()).size())
				.thenComparing(FreeSlot::staffId);
		Map<LocalDateTime, FreeSlot> best = new LinkedHashMap<>();
		for (FreeSlot slot : slots) {
			best.merge(slot.start(), slot, (a, b) -> leastBusyFirst.compare(a, b) <= 0 ? a : b);
		}
		return new ArrayList<>(best.values());
	}
}
