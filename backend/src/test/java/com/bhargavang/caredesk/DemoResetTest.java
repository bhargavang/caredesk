package com.bhargavang.caredesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.bhargavang.caredesk.auth.AppUserRepository;
import com.bhargavang.caredesk.auth.Role;
import com.bhargavang.caredesk.catalog.ServiceOfferingRepository;
import com.bhargavang.caredesk.seed.DemoDataSeeder;
import com.bhargavang.caredesk.support.IntegrationTest;
import com.bhargavang.caredesk.support.MutableClock;
import com.bhargavang.caredesk.support.TestData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** A public demo that runs for weeks keeps its bookings around today, and visitors can't edit the clinic. */
@IntegrationTest
@TestPropertySource(properties = {
		"caredesk.seed.enabled=true",
		"caredesk.seed.daily-reset=true",
		"caredesk.seed.demo-role=STAFF" })
class DemoResetTest {

	@Autowired
	DemoDataSeeder seeder;
	@Autowired
	TestData data;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	MutableClock clock;
	@Autowired
	ServiceOfferingRepository services;
	@Autowired
	AppUserRepository users;

	@BeforeEach
	void seedFresh() {
		clock.reset();
		data.wipe();
		assertThat(seeder.seedOrRefresh()).isTrue();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void theDemoLoginIsFrontDeskOnly() {
		assertThat(users.findByEmailIgnoreCase("demo@caredesk.test").orElseThrow().getRole()).isEqualTo(Role.STAFF);
	}

	@Test
	void theSameDayNothingChanges() {
		List<String> before = references();
		assertThat(seeder.seedOrRefresh()).isFalse();
		assertThat(references()).isEqualTo(before);
	}

	@Test
	void aWeekLaterTheAppointmentsAreRegeneratedAroundTheNewToday() {
		List<Long> serviceIds = services.findAllByOrderByIdAsc().stream().map(s -> s.getId()).toList();
		jdbc.update("insert into email_log (kind, recipient, subject, body, status, created_at) "
				+ "values ('CONFIRMATION', 'someone@example.com', 'Hi', 'Hi', 'LOGGED', current_timestamp)");

		clock.set(MutableClock.START.plusDays(7));
		assertThat(seeder.seedOrRefresh()).isTrue();

		LocalDate today = MutableClock.START.toLocalDate().plusDays(7);
		assertThat(countOn(today)).isGreaterThan(5);
		assertThat(latestDay()).isAfterOrEqualTo(today.plusDays(9));
		assertThat(jdbc.queryForObject("select count(*) from email_log", Integer.class)).isZero();
		// Services and staff keep their ids, so a prerendered page that lists them stays valid.
		assertThat(services.findAllByOrderByIdAsc().stream().map(s -> s.getId()).toList()).isEqualTo(serviceIds);
		assertThat(jdbc.queryForObject("select refreshed_on from demo_state where id = 1", LocalDate.class)).isEqualTo(today);
	}

	@Test
	void instancesRefreshingAtTheSameTimeOnlyDoItOnce() throws Exception {
		clock.set(MutableClock.START.plusDays(1));
		int threads = 4;
		CyclicBarrier start = new CyclicBarrier(threads);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return seeder.seedOrRefresh();
				}));
			}
			int refreshed = 0;
			for (Future<Boolean> result : results) {
				refreshed += result.get() ? 1 : 0;
			}
			assertThat(refreshed).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(countOn(MutableClock.START.toLocalDate().plusDays(1))).isGreaterThan(5);
	}

	private List<String> references() {
		return jdbc.queryForList("select reference from appointment order by reference", String.class);
	}

	private int countOn(LocalDate day) {
		return jdbc.queryForObject("select count(*) from appointment where cast(start_time as date) = ?", Integer.class, day);
	}

	private LocalDate latestDay() {
		LocalDateTime last = jdbc.queryForObject("select max(start_time) from appointment", LocalDateTime.class);
		return last.toLocalDate();
	}
}
