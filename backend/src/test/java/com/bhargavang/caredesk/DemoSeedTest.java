package com.bhargavang.caredesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.bhargavang.caredesk.auth.AppUserRepository;
import com.bhargavang.caredesk.booking.AppointmentRepository;
import com.bhargavang.caredesk.booking.SlotService;
import com.bhargavang.caredesk.catalog.ServiceOfferingRepository;
import com.bhargavang.caredesk.catalog.StaffRepository;
import com.bhargavang.caredesk.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/** The app starts on an empty database and fills it with a believable demo clinic. */
@IntegrationTest
@TestPropertySource(properties = "caredesk.seed.enabled=true")
class DemoSeedTest {

	@Autowired
	ServiceOfferingRepository services;
	@Autowired
	StaffRepository staff;
	@Autowired
	AppointmentRepository appointments;
	@Autowired
	AppUserRepository users;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	SlotService slots;
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void seedsAClinicWithAMonthOfAppointmentsAroundToday() {
		assertThat(services.count()).isEqualTo(6);
		assertThat(staff.count()).isEqualTo(5);
		assertThat(appointments.count()).isGreaterThan(150);

		// Monday 4 March 2030 is "today" in tests; the dashboard needs appointments on it.
		Integer today = jdbc.queryForObject(
				"select count(*) from appointment where cast(start_time as date) = date '2030-03-04'", Integer.class);
		assertThat(today).isGreaterThan(5);

		// Customers still have free slots to pick from next week.
		Long consultation = services.findAllByOrderByIdAsc().getFirst().getId();
		assertThat(slots.findFreeSlots(consultation, LocalDate.of(2030, 3, 12), null)).isNotEmpty();
	}

	@Test
	void demoLoginWorksAndOnlyTheHashIsStored() {
		var demo = users.findByEmailIgnoreCase("demo@caredesk.test").orElseThrow();
		assertThat(demo.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("demo1234");
		assertThat(passwordEncoder.matches("demo1234", demo.getPasswordHash())).isTrue();
	}
}
