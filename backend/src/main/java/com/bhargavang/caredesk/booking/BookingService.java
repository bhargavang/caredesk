package com.bhargavang.caredesk.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.ServiceOfferingRepository;
import com.bhargavang.caredesk.catalog.Staff;
import com.bhargavang.caredesk.catalog.StaffRepository;
import com.bhargavang.caredesk.common.BusinessRuleException;
import com.bhargavang.caredesk.common.NotFoundException;
import com.bhargavang.caredesk.config.CareDeskProperties;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates bookings and changes their status.
 *
 * <p>Double bookings are stopped twice over:
 * <ol>
 * <li>Inside the transaction, the staff row is locked ({@code SELECT ... FOR UPDATE}) and the
 * overlap check runs after the lock, so bookings for one person are checked one at a time,
 * even across several app servers.</li>
 * <li>Every appointment claims its 15-minute blocks in {@code booked_block}, which has a unique key
 * on (staff, block start). If anything ever slipped past the check, the insert fails and the
 * customer gets a clean "just taken" error.</li>
 * </ol>
 */
@Service
public class BookingService {

	private final ServiceOfferingRepository services;
	private final StaffRepository staff;
	private final AppointmentRepository appointments;
	private final SlotService slots;
	private final CareDeskProperties.Booking rules;
	private final ReferenceGenerator references;
	private final Clock clock;

	public BookingService(ServiceOfferingRepository services, StaffRepository staff, AppointmentRepository appointments,
			SlotService slots, ReferenceGenerator references, CareDeskProperties properties, Clock clock) {
		this.services = services;
		this.staff = staff;
		this.appointments = appointments;
		this.slots = slots;
		this.references = references;
		this.rules = properties.booking();
		this.clock = clock;
	}

	@Transactional
	public Appointment book(BookingRequest request) {
		ServiceOffering service = services.findById(request.serviceId())
				.filter(ServiceOffering::isActive)
				.orElseThrow(() -> new NotFoundException("Service not found"));

		// Row lock: concurrent bookings for this staff member wait here until the earlier one commits.
		Staff person = staff.findByIdForUpdate(request.staffId())
				.filter(Staff::isActive)
				.orElseThrow(() -> new NotFoundException("Staff member not found"));
		if (!person.offers(service)) {
			throw new BusinessRuleException(person.getFullName() + " doesn't offer " + service.getName());
		}

		LocalDateTime start = request.start().truncatedTo(ChronoUnit.MINUTES);
		LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
		if (!slots.isOfferedStart(person, service, start)) {
			throw new BusinessRuleException("That time can't be booked. Please choose one of the available slots.");
		}
		if (appointments.countLiveOverlapping(person.getId(), start, end) > 0) {
			throw new SlotUnavailableException();
		}

		Appointment appointment = new Appointment(references.next(), person, service, start,
				request.customerName().trim(), request.customerEmail().trim().toLowerCase(Locale.ROOT),
				blankToNull(request.customerPhone()), blankToNull(request.notes()), LocalDateTime.now(clock));
		appointment.occupyBlocks(rules.blockMinutes());
		try {
			return appointments.saveAndFlush(appointment);
		}
		catch (DataIntegrityViolationException e) {
			if (isBlockConflict(e)) {
				throw new SlotUnavailableException();
			}
			throw e;
		}
	}

	@Transactional
	public Appointment changeStatus(Long id, AppointmentStatus newStatus) {
		Appointment appointment = appointments.findWithDetailsById(id)
				.orElseThrow(() -> new NotFoundException("Appointment not found"));
		if (appointment.getStatus() == newStatus) {
			return appointment;
		}
		if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
			throw new BusinessRuleException("This appointment was cancelled and its time may be booked again, so it can't be changed.");
		}
		boolean startsAfterToday = appointment.getStartTime().toLocalDate().isAfter(LocalDate.now(clock));
		if ((newStatus == AppointmentStatus.DONE || newStatus == AppointmentStatus.NO_SHOW) && startsAfterToday) {
			throw new BusinessRuleException("Appointments can be marked done or no-show from their day onwards.");
		}
		if (newStatus == AppointmentStatus.CANCELLED) {
			appointment.cancel();
		}
		else {
			appointment.setStatus(newStatus);
		}
		return appointment;
	}

	private static boolean isBlockConflict(DataIntegrityViolationException e) {
		String message = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
		return message.contains("uq_booked_block_staff_start");
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
