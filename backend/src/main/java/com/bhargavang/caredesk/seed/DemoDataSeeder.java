package com.bhargavang.caredesk.seed;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import com.bhargavang.caredesk.auth.AppUser;
import com.bhargavang.caredesk.auth.AppUserRepository;
import com.bhargavang.caredesk.auth.Role;
import com.bhargavang.caredesk.booking.Appointment;
import com.bhargavang.caredesk.booking.AppointmentRepository;
import com.bhargavang.caredesk.booking.AppointmentStatus;
import com.bhargavang.caredesk.booking.ReferenceGenerator;
import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.ServiceOfferingRepository;
import com.bhargavang.caredesk.catalog.Staff;
import com.bhargavang.caredesk.catalog.StaffRepository;
import com.bhargavang.caredesk.catalog.WorkingHours;
import com.bhargavang.caredesk.config.CareDeskProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty database with a fictional clinic: services, staff, working hours,
 * a demo login, and about a month of appointments around today, so the dashboard
 * and chart never look empty. Does nothing if services already exist, unless the
 * daily reset is on: then the appointments are regenerated once a day.
 */
@Component
@ConditionalOnProperty(name = "caredesk.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private static final String[] FIRST_NAMES = { "Aarav", "Diya", "Rohan", "Meera", "Arjun", "Kavya", "Vikram",
			"Sneha", "Aditya", "Ishita", "Karthik", "Pooja", "Nikhil", "Lakshmi", "Siddharth", "Ananya", "Rahul",
			"Divya", "Varun", "Nandini", "Imran", "Sara", "Joseph", "Fatima", "Harsh", "Tanvi", "Manoj", "Asha",
			"Pranav", "Ritu", "Suresh", "Neha", "Abdul", "Grace", "Deepak", "Shruti" };
	private static final String[] LAST_NAMES = { "Sharma", "Iyer", "Reddy", "Kulkarni", "Shetty", "Menon", "Gowda",
			"Pillai", "Khan", "Das", "Bhat", "Hegde", "Naidu", "Thomas", "Patil", "Rao", "Kapoor", "Joshi", "Fernandes",
			"Krishnan" };
	private static final String[] NOTES = { "First visit", "Follow-up from last month", "Please call before the visit",
			"Bringing previous reports", "Prefers Kannada or English" };

	private final ServiceOfferingRepository services;
	private final StaffRepository staff;
	private final AppointmentRepository appointments;
	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final ReferenceGenerator references;
	private final CareDeskProperties properties;
	private final Clock clock;
	private final JdbcTemplate jdbc;

	public DemoDataSeeder(ServiceOfferingRepository services, StaffRepository staff, AppointmentRepository appointments,
			AppUserRepository users, PasswordEncoder passwordEncoder, ReferenceGenerator references,
			CareDeskProperties properties, Clock clock, JdbcTemplate jdbc) {
		this.services = services;
		this.staff = staff;
		this.appointments = appointments;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.references = references;
		this.properties = properties;
		this.clock = clock;
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seedOrRefresh();
	}

	/**
	 * Seeds an empty database, or regenerates the appointments if the daily reset is on and they
	 * were last made before today. Runs at startup and from {@link DemoResetJob}. The demo_state row
	 * is locked first, so instances that start together take turns instead of seeding twice.
	 *
	 * @return whether anything was written
	 */
	@Transactional
	public boolean seedOrRefresh() {
		LocalDate today = LocalDate.now(clock);
		LocalDate refreshedOn = jdbc.query("select refreshed_on from demo_state where id = 1 for update",
				rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null);

		if (services.count() == 0) {
			seedClinic();
		}
		else if (properties.seed().dailyReset() && (refreshedOn == null || refreshedOn.isBefore(today))) {
			refreshAppointments();
		}
		else {
			log.info("Database already has data, skipping demo seed");
			return false;
		}
		jdbc.update("update demo_state set refreshed_on = ? where id = 1", today);
		return true;
	}

	private void refreshAppointments() {
		jdbc.update("delete from email_log");
		jdbc.update("delete from booked_block");
		jdbc.update("delete from appointment");
		List<Staff> team = staff.findAll().stream()
				.filter(Staff::isActive)
				.sorted((a, b) -> a.getId().compareTo(b.getId()))
				.toList();
		int created = seedAppointments(team);
		log.info("Refreshed the demo appointments around today: {} appointments", created);
	}

	private void seedClinic() {
		ServiceOffering consult = service("General consultation",
				"Fevers, coughs, check-ups, prescriptions and referrals.", 30, 600);
		ServiceOffering child = service("Child health check",
				"Growth, development and general health for children under 12.", 30, 700);
		ServiceOffering vaccine = service("Vaccination",
				"Routine and travel vaccines. Bring your vaccination card if you have one.", 15, 350);
		ServiceOffering dental = service("Dental check-up and cleaning",
				"Examination, scaling and polishing, with advice on any treatment needed.", 45, 1200);
		ServiceOffering physio = service("Physiotherapy session",
				"Back, neck, knee and sports injuries. Wear comfortable clothes.", 60, 1000);
		ServiceOffering skin = service("Skin consultation",
				"Acne, rashes, hair fall and other skin concerns.", 30, 800);

		List<DayOfWeek> monSat = List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY);
		List<DayOfWeek> monFri = List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);

		Staff rao = person("Dr. Ananya Rao", "General physician",
				"Family doctor with 12 years in general practice. Sees adults and children.",
				Set.of(consult, child, vaccine));
		hours(rao, monFri, "09:00-13:00", "14:00-17:00");
		hours(rao, List.of(SATURDAY), "09:00-13:00");

		Staff mehta = person("Dr. Kiran Mehta", "Dentist",
				"General dentistry, with a gentle approach for nervous patients.", Set.of(dental));
		hours(mehta, List.of(MONDAY, TUESDAY, THURSDAY, FRIDAY, SATURDAY), "10:00-14:00", "15:00-19:00");

		Staff nair = person("Priya Nair", "Physiotherapist",
				"Sports injuries and back pain. Early morning and evening sessions.", Set.of(physio));
		hours(nair, monFri, "07:30-11:30", "16:00-20:00");

		Staff siddiqui = person("Dr. Farah Siddiqui", "Dermatologist",
				"Skin, hair and nail conditions for all ages.", Set.of(skin));
		hours(siddiqui, List.of(TUESDAY, WEDNESDAY, THURSDAY, SATURDAY), "11:00-15:00", "16:00-18:00");

		Staff joseph = person("Meera Joseph", "Staff nurse",
				"Runs the vaccination clinic and helps with child health checks.", Set.of(vaccine, child));
		hours(joseph, monSat, "09:00-13:00", "14:00-16:00");

		List<Staff> team = staff.saveAll(List.of(rao, mehta, nair, siddiqui, joseph));

		Role demoRole = properties.seed().demoRole() == null ? Role.ADMIN : properties.seed().demoRole();
		users.save(new AppUser(properties.seed().demoEmail().toLowerCase(Locale.ROOT),
				demoRole == Role.ADMIN ? "Demo Admin" : "Demo front desk",
				passwordEncoder.encode(properties.seed().demoPassword()), demoRole));

		int created = seedAppointments(team);
		log.info("Seeded demo clinic: {} services, {} staff, {} appointments. Staff login: {}",
				services.count(), team.size(), created, properties.seed().demoEmail());
	}

	private int seedAppointments(List<Staff> team) {
		Random random = new Random(20261109L);
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		int step = properties.booking().slotStepMinutes();
		Set<String> usedReferences = new HashSet<>();
		List<Appointment> batch = new ArrayList<>();

		for (LocalDate day = today.minusDays(20); !day.isAfter(today.plusDays(10)); day = day.plusDays(1)) {
			long daysAhead = day.toEpochDay() - today.toEpochDay();
			// Past days are fairly full; the further ahead, the fewer bookings so far.
			double fill = daysAhead <= 0 ? 0.55 : Math.max(0.12, 0.5 - daysAhead * 0.04);

			for (Staff person : team) {
				List<ServiceOffering> offered = person.getServices().stream()
						.sorted((a, b) -> a.getId().compareTo(b.getId()))
						.toList();
				for (WorkingHours block : person.getWorkingHours()) {
					if (block.getDayOfWeek() != day.getDayOfWeek()) {
						continue;
					}
					LocalDateTime t = day.atTime(block.getStartTime());
					LocalDateTime blockEnd = day.atTime(block.getEndTime());
					while (t.isBefore(blockEnd)) {
						ServiceOffering service = offered.get(random.nextInt(offered.size()));
						LocalDateTime end = t.plusMinutes(service.getDurationMinutes());
						if (end.isAfter(blockEnd) || random.nextDouble() > fill) {
							t = t.plusMinutes(step);
							continue;
						}
						batch.add(appointment(person, service, t, now, random, usedReferences));
						long slots = (service.getDurationMinutes() + step - 1) / step;
						t = t.plusMinutes(slots * step);
					}
				}
			}
		}
		appointments.saveAll(batch);
		return batch.size();
	}

	private Appointment appointment(Staff person, ServiceOffering service, LocalDateTime start, LocalDateTime now,
			Random random, Set<String> usedReferences) {
		String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
		String last = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
		String email = (first + "." + last + "@example.com").toLowerCase(Locale.ROOT);
		String note = random.nextDouble() < 0.15 ? NOTES[random.nextInt(NOTES.length)] : null;

		LocalDateTime createdAt = start.minusDays(1 + random.nextInt(12)).minusMinutes(random.nextInt(600));
		if (!createdAt.isBefore(now)) {
			createdAt = now.minusHours(1 + random.nextInt(48));
		}

		String reference;
		do {
			reference = references.random();
		}
		while (!usedReferences.add(reference));

		Appointment a = new Appointment(reference, person, service, start, first + " " + last, email, null, note,
				createdAt);
		// Demo history: pretend confirmations already went out so startup doesn't send dozens.
		a.setConfirmationSentAt(createdAt.plusMinutes(1));

		boolean finished = !a.getEndTime().isAfter(now);
		boolean isToday = start.toLocalDate().equals(now.toLocalDate());
		double r = random.nextDouble();
		if (finished && isToday) {
			// Some of today's earlier visits are still waiting for the front desk to mark them.
			a.setStatus(r < 0.55 ? AppointmentStatus.DONE : r < 0.65 ? AppointmentStatus.NO_SHOW : AppointmentStatus.BOOKED);
		}
		else if (finished) {
			a.setStatus(r < 0.84 ? AppointmentStatus.DONE : r < 0.92 ? AppointmentStatus.NO_SHOW : AppointmentStatus.CANCELLED);
		}
		else if (r > 0.93) {
			a.setStatus(AppointmentStatus.CANCELLED);
		}

		if (!start.toLocalDate().isAfter(now.toLocalDate())) {
			LocalDateTime reminderAt = start.toLocalDate().minusDays(1).atTime(18, 0);
			if (reminderAt.isAfter(createdAt)) {
				a.setReminderSentAt(reminderAt);
			}
		}

		if (a.isLive()) {
			a.occupyBlocks(properties.booking().blockMinutes());
		}
		return a;
	}

	private ServiceOffering service(String name, String description, int minutes, int rupees) {
		return services.save(new ServiceOffering(name, description, minutes, BigDecimal.valueOf(rupees)));
	}

	private static Staff person(String name, String title, String bio, Set<ServiceOffering> offered) {
		String email = name.toLowerCase(Locale.ROOT).replace("dr. ", "").replace(' ', '.') + "@caredesk.test";
		Staff s = new Staff(name, title, email, bio);
		s.getServices().addAll(offered);
		return s;
	}

	private static void hours(Staff person, List<DayOfWeek> days, String... ranges) {
		for (DayOfWeek day : days) {
			for (String range : ranges) {
				String[] parts = range.split("-");
				person.addWorkingHours(new WorkingHours(day, LocalTime.parse(parts[0]), LocalTime.parse(parts[1])));
			}
		}
	}
}
