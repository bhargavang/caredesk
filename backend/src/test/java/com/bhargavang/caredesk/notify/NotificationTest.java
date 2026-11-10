package com.bhargavang.caredesk.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.bhargavang.caredesk.booking.Appointment;
import com.bhargavang.caredesk.booking.AppointmentStatus;
import com.bhargavang.caredesk.booking.BookingRequest;
import com.bhargavang.caredesk.booking.BookingService;
import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.Staff;
import com.bhargavang.caredesk.support.CapturingEmailSender;
import com.bhargavang.caredesk.support.IntegrationTest;
import com.bhargavang.caredesk.support.MutableClock;
import com.bhargavang.caredesk.support.TestData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class NotificationTest {

	@Autowired
	NotificationService notifications;
	@Autowired
	BookingService bookings;
	@Autowired
	EmailLogRepository emailLog;
	@Autowired
	CapturingEmailSender sender;
	@Autowired
	TestData data;
	@Autowired
	MutableClock clock;

	ServiceOffering consult;
	Staff rao;

	@BeforeEach
	void setUp() {
		clock.reset(); // Monday 4 March 2030, 08:00
		data.wipe();
		sender.sent.clear();
		sender.failing = false;
		consult = data.service("Consultation", 30);
		rao = data.staff("Dr Rao", List.of(consult), "09:00-17:00");
	}

	@Test
	void eachBookingGetsExactlyOneConfirmation() {
		Appointment a = book(LocalDateTime.of(2030, 3, 6, 10, 0), "meera@example.com");

		assertThat(notifications.sendPendingConfirmations()).isEqualTo(1);
		assertThat(notifications.sendPendingConfirmations()).isZero();

		assertThat(sender.sent).singleElement().satisfies(m -> {
			assertThat(m.to()).isEqualTo("meera@example.com");
			assertThat(m.subject()).startsWith("Booking confirmed: Consultation");
			assertThat(m.body()).contains(a.getReference(), "Dr Rao", "Wednesday 6 March 2030 at 10:00 am");
		});
		assertThat(emailLog.findByAppointmentIdOrderByIdAsc(a.getId()))
				.singleElement()
				.satisfies(log -> assertThat(log.getStatus()).isEqualTo("SENT"));
	}

	@Test
	void remindersGoOutTheDayBeforeButNotForBookingsMadeThatDay() {
		Appointment early = book(LocalDateTime.of(2030, 3, 6, 10, 0), "early@example.com"); // booked Monday
		clock.set(LocalDateTime.of(2030, 3, 5, 9, 0)); // Tuesday
		book(LocalDateTime.of(2030, 3, 6, 11, 0), "sameday@example.com"); // booked Tuesday for Wednesday
		book(LocalDateTime.of(2030, 3, 7, 11, 0), "thursday@example.com"); // not tomorrow

		assertThat(notifications.sendDayBeforeReminders()).isEqualTo(1);
		assertThat(notifications.sendDayBeforeReminders()).isZero();
		assertThat(sender.sent).singleElement().satisfies(m -> {
			assertThat(m.to()).isEqualTo(early.getCustomerEmail());
			assertThat(m.subject()).isEqualTo("Reminder: your appointment tomorrow at 10:00 am");
		});
	}

	@Test
	void cancelledAppointmentsGetNothing() {
		Appointment a = book(LocalDateTime.of(2030, 3, 6, 10, 0), "gone@example.com");
		bookings.changeStatus(a.getId(), AppointmentStatus.CANCELLED);

		assertThat(notifications.sendPendingConfirmations()).isZero();
		clock.set(LocalDateTime.of(2030, 3, 5, 9, 0));
		assertThat(notifications.sendDayBeforeReminders()).isZero();
		assertThat(sender.sent).isEmpty();
	}

	@Test
	void aFailedSendIsLoggedAndRetriedOnTheNextRun() {
		Appointment a = book(LocalDateTime.of(2030, 3, 6, 10, 0), "retry@example.com");

		sender.failing = true;
		assertThat(notifications.sendPendingConfirmations()).isZero();
		sender.failing = false;
		assertThat(notifications.sendPendingConfirmations()).isEqualTo(1);

		assertThat(emailLog.findByAppointmentIdOrderByIdAsc(a.getId()))
				.extracting(EmailLog::getStatus)
				.containsExactly("FAILED", "SENT");
	}

	@Test
	void severalInstancesRunningTheJobAtOnceNeverSendTwice() throws Exception {
		for (int i = 0; i < 8; i++) {
			book(LocalDateTime.of(2030, 3, 6, 9, 0).plusMinutes(30L * i), "customer" + i + "@example.com");
		}

		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> runs = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			runs.add(pool.submit(() -> {
				go.await();
				return notifications.sendPendingConfirmations();
			}));
		}
		go.countDown();
		int total = 0;
		for (Future<Integer> run : runs) {
			total += run.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();

		assertThat(total).isEqualTo(8);
		assertThat(sender.sent).hasSize(8);
		assertThat(sender.sent.stream().map(EmailMessage::to).distinct()).hasSize(8);
	}

	private Appointment book(LocalDateTime start, String email) {
		return bookings.book(new BookingRequest(consult.getId(), rao.getId(), start, "Meera Iyer", email, null, null));
	}
}
