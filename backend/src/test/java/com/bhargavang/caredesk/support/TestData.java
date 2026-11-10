package com.bhargavang.caredesk.support;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.bhargavang.caredesk.auth.AppUser;
import com.bhargavang.caredesk.auth.AppUserRepository;
import com.bhargavang.caredesk.auth.Role;
import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.ServiceOfferingRepository;
import com.bhargavang.caredesk.catalog.Staff;
import com.bhargavang.caredesk.catalog.StaffRepository;
import com.bhargavang.caredesk.catalog.WorkingHours;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Builds a tiny clinic for tests and wipes it between them. */
public class TestData {

	public static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ServiceOfferingRepository services;
	@Autowired
	private StaffRepository staff;
	@Autowired
	private AppUserRepository users;
	@Autowired
	private PasswordEncoder passwordEncoder;

	public void wipe() {
		for (String table : List.of("booked_block", "email_log", "appointment", "working_hours", "staff_service", "staff",
				"service_offering", "app_user")) {
			jdbc.update("delete from " + table);
		}
	}

	public ServiceOffering service(String name, int minutes) {
		return services.save(new ServiceOffering(name, null, minutes, BigDecimal.valueOf(500)));
	}

	/** A staff member who works the given ranges ("09:00-12:00") Monday to Friday. */
	public Staff staff(String name, List<ServiceOffering> offered, String... ranges) {
		Staff s = new Staff(name, "Doctor", null, null);
		s.getServices().addAll(offered);
		for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY)) {
			for (String range : ranges) {
				String[] p = range.split("-");
				s.addWorkingHours(new WorkingHours(day, LocalTime.parse(p[0]), LocalTime.parse(p[1])));
			}
		}
		return staff.save(s);
	}

	public AppUser user(String email, Role role) {
		return users.save(new AppUser(email, role.name() + " user", passwordEncoder.encode(PASSWORD), role));
	}
}
