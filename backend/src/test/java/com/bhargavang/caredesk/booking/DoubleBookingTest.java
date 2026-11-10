package com.bhargavang.caredesk.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.Staff;
import com.bhargavang.caredesk.common.BusinessRuleException;
import com.bhargavang.caredesk.support.IntegrationTest;
import com.bhargavang.caredesk.support.MutableClock;
import com.bhargavang.caredesk.support.TestData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the two layers of the double-booking guard: the locked check in
 * {@link BookingService} and the unique key on booked_block underneath it.
 */
@IntegrationTest
class DoubleBookingTest {

	static final LocalDate TUESDAY = LocalDate.of(2030, 3, 5);
	static final LocalDateTime TEN_AM = TUESDAY.atTime(10, 0);

	@Autowired
	BookingService bookings;
	@Autowired
	AppointmentRepository appointments;
	@Autowired
	ReferenceGenerator references;
	@Autowired
	TransactionTemplate tx;
	@Autowired
	JdbcTemplate jdbc;
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
		rao = data.staff("Dr Rao", List.of(consult, longSession), "09:00-13:00");
	}

	@Test
	void aBookingHoldsOneBlockPerFifteenMinutes() {
		Appointment a = book(longSession, TEN_AM, "first@example.com");
		assertThat(blocksFor(a)).isEqualTo(4);
	}

	@Test
	void theSameSlotCannotBeBookedTwice() {
		book(consult, TEN_AM, "first@example.com");

		assertThatThrownBy(() -> book(consult, TEN_AM, "second@example.com"))
				.isInstanceOf(SlotUnavailableException.class);
		assertThat(liveAppointments()).isEqualTo(1);
	}

	@Test
	void anOverlappingSlotWithADifferentStartIsRejectedToo() {
		book(longSession, TEN_AM, "first@example.com"); // 10:00-11:00

		assertThatThrownBy(() -> book(consult, TUESDAY.atTime(10, 30), "second@example.com"))
				.isInstanceOf(SlotUnavailableException.class);
	}

	@Test
	void backToBackBookingsAreFine() {
		book(longSession, TEN_AM, "first@example.com"); // 10:00-11:00
		book(consult, TUESDAY.atTime(11, 0), "second@example.com");
		book(consult, TUESDAY.atTime(9, 30), "third@example.com");
		assertThat(liveAppointments()).isEqualTo(3);
	}

	@Test
	void aCancelledSlotCanBeBookedAgain() {
		Appointment first = book(consult, TEN_AM, "first@example.com");
		bookings.changeStatus(first.getId(), AppointmentStatus.CANCELLED);

		Appointment second = book(consult, TEN_AM, "second@example.com");

		assertThat(blocksFor(first)).isZero();
		assertThat(blocksFor(second)).isEqualTo(2);
	}

	@Test
	void onlyOfferedTimesCanBeBooked() {
		assertThatThrownBy(() -> book(consult, TUESDAY.atTime(10, 15), "x@example.com")) // off the 30-minute grid
				.isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> book(consult, TUESDAY.atTime(13, 0), "x@example.com")) // after hours
				.isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> book(longSession, TUESDAY.atTime(12, 30), "x@example.com")) // would run past 13:00
				.isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> book(consult, LocalDateTime.of(2030, 3, 4, 8, 30), "x@example.com")) // too soon
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void twelvePeopleClickingTheSameSlotAtOnceGetOneBooking() throws Exception {
		List<Callable<Appointment>> attempts = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			String email = "racer" + i + "@example.com";
			attempts.add(() -> book(consult, TEN_AM, email));
		}

		Outcome outcome = runTogether(attempts);

		assertThat(outcome.succeeded()).isEqualTo(1);
		assertThat(outcome.failures()).hasSize(11).allMatch(e -> e instanceof SlotUnavailableException);
		assertThat(liveAppointments()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from booked_block", Integer.class)).isEqualTo(2);
	}

	@Test
	void overlappingRequestsWithDifferentStartsRacingGetOneBooking() throws Exception {
		// 09:30-10:30, 10:00-11:00 and 10:00-10:30 all clash with each other.
		List<Callable<Appointment>> attempts = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			String email = "racer" + i + "@example.com";
			switch (i % 3) {
				case 0 -> attempts.add(() -> book(longSession, TUESDAY.atTime(9, 30), email));
				case 1 -> attempts.add(() -> book(longSession, TEN_AM, email));
				default -> attempts.add(() -> book(consult, TEN_AM, email));
			}
		}

		Outcome outcome = runTogether(attempts);

		List<Appointment> live = appointments.findAll().stream().filter(Appointment::isLive).toList();
		for (int i = 0; i < live.size(); i++) {
			for (int j = i + 1; j < live.size(); j++) {
				Appointment a = live.get(i);
				Appointment b = live.get(j);
				assertThat(a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime()))
						.as("%s and %s overlap", a.getReference(), b.getReference())
						.isFalse();
			}
		}
		assertThat(outcome.succeeded()).isEqualTo(1);
		assertThat(outcome.failures()).allMatch(e -> e instanceof SlotUnavailableException);
	}

	@Test
	void theDatabaseRejectsADoubleBookingEvenWhenTheAppCheckIsSkipped() {
		tx.executeWithoutResult(s -> appointments.saveAndFlush(rawAppointment(longSession, TEN_AM)));

		// Straight to the repository: no lock, no overlap check. Only the unique key stands in the way.
		assertThatThrownBy(() -> tx.executeWithoutResult(
				s -> appointments.saveAndFlush(rawAppointment(consult, TUESDAY.atTime(10, 30)))))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasStackTraceContaining("uq_booked_block_staff_start");
		assertThat(liveAppointments()).isEqualTo(1);
	}

	@Test
	void theUniqueKeyCatchesARaceThatSlipsPastTheCheck() throws Exception {
		// Two transactions both run the overlap check before either has inserted,
		// so both think the slot is free. The unique key has to stop the second one.
		CyclicBarrier bothChecked = new CyclicBarrier(2);
		Callable<Appointment> attempt = () -> tx.execute(status -> {
			long overlapping = appointments.countLiveOverlapping(rao.getId(), TEN_AM, TEN_AM.plusMinutes(30));
			assertThat(overlapping).isZero();
			await(bothChecked);
			return appointments.saveAndFlush(rawAppointment(consult, TEN_AM));
		});

		Outcome outcome = runTogether(List.of(attempt, attempt));

		assertThat(outcome.succeeded()).isEqualTo(1);
		assertThat(outcome.failures()).singleElement().isInstanceOf(DataAccessException.class);
		assertThat(liveAppointments()).isEqualTo(1);
	}

	private Appointment book(ServiceOffering service, LocalDateTime start, String email) {
		return bookings.book(new BookingRequest(service.getId(), rao.getId(), start, "Test Customer", email, null, null));
	}

	private Appointment rawAppointment(ServiceOffering service, LocalDateTime start) {
		Appointment a = new Appointment(references.random(), rao, service, start, "Raw Insert", "raw@example.com",
				null, null, LocalDateTime.of(2030, 3, 4, 8, 0));
		a.occupyBlocks(15);
		return a;
	}

	private int blocksFor(Appointment a) {
		return jdbc.queryForObject("select count(*) from booked_block where appointment_id = ?", Integer.class, a.getId());
	}

	private long liveAppointments() {
		return jdbc.queryForObject("select count(*) from appointment where status <> 'CANCELLED'", Long.class);
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await(10, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	record Outcome(int succeeded, List<Throwable> failures) {
	}

	/** Starts all tasks at the same moment and waits for them. */
	private static <T> Outcome runTogether(List<Callable<T>> tasks) throws InterruptedException {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(pool.submit(() -> {
					go.await();
					return task.call();
				}));
			}
			go.countDown();
			int ok = 0;
			List<Throwable> failures = new ArrayList<>();
			for (Future<T> f : futures) {
				try {
					f.get(30, TimeUnit.SECONDS);
					ok++;
				}
				catch (ExecutionException e) {
					failures.add(e.getCause());
				}
				catch (java.util.concurrent.TimeoutException e) {
					failures.add(e);
				}
			}
			return new Outcome(ok, failures);
		}
		finally {
			pool.shutdownNow();
		}
	}
}
