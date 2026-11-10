package com.bhargavang.caredesk.notify;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.bhargavang.caredesk.booking.Appointment;
import com.bhargavang.caredesk.config.CareDeskProperties;

import org.springframework.stereotype.Component;

/** Plain-text email bodies. Plain text keeps them readable everywhere and out of spam folders. */
@Component
public class EmailComposer {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

	private final CareDeskProperties.Clinic clinic;

	public EmailComposer(CareDeskProperties properties) {
		this.clinic = properties.clinic();
	}

	public EmailMessage compose(EmailKind kind, Appointment a) {
		String firstName = a.getCustomerName().trim().split("\\s+")[0];
		String when = a.getStartTime().format(DAY) + " at " + a.getStartTime().format(TIME).toLowerCase(Locale.ENGLISH);
		String details = """
				Service:   %s (%d min)
				With:      %s
				When:      %s
				Reference: %s

				%s
				%s
				Phone: %s""".formatted(a.getService().getName(), a.getService().getDurationMinutes(),
				a.getStaff().getFullName(), when, a.getReference(), clinic.name(), clinic.address(), clinic.phone());

		return switch (kind) {
			case CONFIRMATION -> new EmailMessage(a.getCustomerEmail(),
					"Booking confirmed: " + a.getService().getName() + " on " + a.getStartTime().format(DAY),
					"""
							Hi %s,

							Your appointment is booked.

							%s

							If you need to change or cancel, please call us and quote your reference.
							""".formatted(firstName, details));
			case REMINDER -> new EmailMessage(a.getCustomerEmail(),
					"Reminder: your appointment tomorrow at " + a.getStartTime().format(TIME).toLowerCase(Locale.ENGLISH),
					"""
							Hi %s,

							A quick reminder about your appointment tomorrow.

							%s

							Please arrive 5 minutes early. If you can't make it, call us so we can offer the time to someone else.
							""".formatted(firstName, details));
		};
	}
}
