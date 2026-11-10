package com.bhargavang.caredesk.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.Staff;
import com.bhargavang.caredesk.support.IntegrationTest;
import com.bhargavang.caredesk.support.MutableClock;
import com.bhargavang.caredesk.support.TestData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SlotServiceTest {

	// The test clock starts on Monday 4 March 2030 at 08:00.
	static final LocalDate TUESDAY = LocalDate.of(2030, 3, 5);

	@Autowired
	SlotService slots;
	@Autowired
	BookingService bookings;
	@Autowired
	TestData data;
	@Autowired
	MutableClock clock;

	ServiceOffering consult;
	ServiceOffering longSession;
	Staff rao;

	@BeforeEach
	void setUp() {
		clock.reset();
		data.wipe();
		consult = data.service("Consultation", 30);
		longSession = data.service("Long session", 60);
		rao = data.staff("Dr Rao", List.of(consult, longSession), "09:00-12:00");
	}

	@Test
	void offersEveryStepInsideWorkingHoursWhereTheWholeAppointmentFits() {
		assertThat(times(slots.findFreeSlots(consult.getId(), TUESDAY, rao.getId())))
				.containsExactly("09:00", "09:30", "10:00", "10:30", "11:00", "11:30");
		assertThat(times(slots.findFreeSlots(longSession.getId(), TUESDAY, rao.getId())))
				.containsExactly("09:00", "09:30", "10:00", "10:30", "11:00");
	}

	@Test
	void aBreakSplitsTheDay() {
		Staff nair = data.staff("Priya Nair", List.of(longSession), "09:00-11:00", "12:00-13:00");
		assertThat(times(slots.findFreeSlots(longSession.getId(), TUESDAY, nair.getId())))
				.containsExactly("09:00", "09:30", "10:00", "12:00");
	}

	@Test
	void hidesEveryStartThatWouldOverlapABooking() {
		book(longSession, rao, TUESDAY.atTime(10, 0)); // 10:00-11:00

		assertThat(times(slots.findFreeSlots(consult.getId(), TUESDAY, rao.getId())))
				.containsExactly("09:00", "09:30", "11:00", "11:30");
		// A 60-minute session at 09:30 would run into the 10:00 booking.
		assertThat(times(slots.findFreeSlots(longSession.getId(), TUESDAY, rao.getId())))
				.containsExactly("09:00", "11:00");
	}

	@Test
	void cancellingMakesTheTimeFreeAgain() {
		Appointment booked = book(longSession, rao, TUESDAY.atTime(10, 0));
		bookings.changeStatus(booked.getId(), AppointmentStatus.CANCELLED);

		assertThat(times(slots.findFreeSlots(consult.getId(), TUESDAY, rao.getId())))
				.contains("10:00", "10:30");
	}

	@Test
	void respectsTheMinimumLeadTimeToday() {
		clock.set(LocalDateTime.of(2030, 3, 4, 9, 10)); // lead time is 60 minutes
		assertThat(times(slots.findFreeSlots(consult.getId(), clock.instant().atZone(MutableClock.ZONE).toLocalDate(),
				rao.getId()))).containsExactly("10:30", "11:00", "11:30");
	}

	@Test
	void nothingInThePastOnDaysOffOrTooFarAhead() {
		assertThat(slots.findFreeSlots(consult.getId(), LocalDate.of(2030, 3, 1), rao.getId())).isEmpty(); // past
		assertThat(slots.findFreeSlots(consult.getId(), LocalDate.of(2030, 3, 9), rao.getId())).isEmpty(); // Saturday
		assertThat(slots.findFreeSlots(consult.getId(), LocalDate.of(2030, 5, 6), rao.getId())).isEmpty(); // > 60 days
	}

	@Test
	void anyoneAvailableGivesOneSlotPerTimeToTheLeastBusyPerson() {
		Staff mehta = data.staff("Dr Mehta", List.of(consult), "09:00-10:00");
		book(consult, mehta, TUESDAY.atTime(9, 30));

		List<FreeSlot> any = slots.findFreeSlots(consult.getId(), TUESDAY, null);

		assertThat(times(any)).containsExactly("09:00", "09:30", "10:00", "10:30", "11:00", "11:30");
		// Both are free at 09:00; Dr Rao has nothing booked yet, so Dr Rao gets it.
		assertThat(any.get(0).staffName()).isEqualTo("Dr Rao");
	}

	private Appointment book(ServiceOffering service, Staff staff, LocalDateTime start) {
		return bookings.book(new BookingRequest(service.getId(), staff.getId(), start, "Asha Shetty",
				"asha@example.com", null, null));
	}

	private static List<String> times(List<FreeSlot> free) {
		return free.stream().map(s -> s.start().toLocalTime()).map(LocalTime::toString).toList();
	}
}
