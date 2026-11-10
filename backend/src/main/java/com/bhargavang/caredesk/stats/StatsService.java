package com.bhargavang.caredesk.stats;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.bhargavang.caredesk.booking.AppointmentRepository;
import com.bhargavang.caredesk.booking.AppointmentStatus;
import com.bhargavang.caredesk.booking.StatusAtTime;
import com.bhargavang.caredesk.common.BusinessRuleException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

	static final int MAX_DAYS = 92;

	private final AppointmentRepository appointments;
	private final Clock clock;

	public StatsService(AppointmentRepository appointments, Clock clock) {
		this.appointments = appointments;
		this.clock = clock;
	}

	public record DayCount(LocalDate date, int booked, int done, int noShow, int cancelled, int total) {
	}

	/**
	 * @param noShowRate share of finished appointments (done + no-show) that were no-shows, 0 to 1
	 */
	public record Summary(int today, int todayRemaining, int next7Days, int done, int noShow, int cancelled,
			double noShowRate) {
	}

	public record DailyStats(LocalDate from, LocalDate to, List<DayCount> days, Summary summary) {
	}

	/** Appointments per day between two dates (inclusive), with zero-filled gaps. */
	@Transactional(readOnly = true)
	public DailyStats daily(LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw new BusinessRuleException("'to' must not be before 'from'");
		}
		if (from.plusDays(MAX_DAYS).isBefore(to)) {
			throw new BusinessRuleException("Pick a range of " + MAX_DAYS + " days or fewer");
		}

		Map<LocalDate, EnumMap<AppointmentStatus, Integer>> counts = new TreeMap<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			counts.put(d, new EnumMap<>(AppointmentStatus.class));
		}
		for (StatusAtTime row : appointments.findStatusesBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
			counts.get(row.startTime().toLocalDate()).merge(row.status(), 1, Integer::sum);
		}

		List<DayCount> days = new ArrayList<>();
		int done = 0;
		int noShow = 0;
		int cancelled = 0;
		for (Map.Entry<LocalDate, EnumMap<AppointmentStatus, Integer>> e : counts.entrySet()) {
			EnumMap<AppointmentStatus, Integer> c = e.getValue();
			int b = c.getOrDefault(AppointmentStatus.BOOKED, 0);
			int dn = c.getOrDefault(AppointmentStatus.DONE, 0);
			int ns = c.getOrDefault(AppointmentStatus.NO_SHOW, 0);
			int cx = c.getOrDefault(AppointmentStatus.CANCELLED, 0);
			days.add(new DayCount(e.getKey(), b, dn, ns, cx, b + dn + ns));
			done += dn;
			noShow += ns;
			cancelled += cx;
		}
		return new DailyStats(from, to, days, summary(done, noShow, cancelled));
	}

	private Summary summary(int done, int noShow, int cancelled) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		List<StatusAtTime> week = appointments.findStatusesBetween(today.atStartOfDay(), today.plusDays(8).atStartOfDay());
		int todayCount = 0;
		int todayRemaining = 0;
		int next7 = 0;
		for (StatusAtTime row : week) {
			if (row.status() == AppointmentStatus.CANCELLED) {
				continue;
			}
			if (row.startTime().toLocalDate().equals(today)) {
				todayCount++;
				if (row.status() == AppointmentStatus.BOOKED && row.startTime().isAfter(now)) {
					todayRemaining++;
				}
			}
			else {
				next7++;
			}
		}
		double rate = done + noShow == 0 ? 0 : (double) noShow / (done + noShow);
		return new Summary(todayCount, todayRemaining, next7, done, noShow, cancelled, Math.round(rate * 1000) / 1000.0);
	}
}
