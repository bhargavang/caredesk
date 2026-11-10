package com.bhargavang.caredesk.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public final class AppointmentDtos {

	private AppointmentDtos() {
	}

	/** What the customer sees after booking. No email or phone, since it's looked up by reference. */
	public record BookingConfirmation(String reference, AppointmentStatus status, String serviceName,
			int durationMinutes, BigDecimal price, String staffName, String staffTitle, LocalDateTime start,
			LocalDateTime end, String customerFirstName) {

		public static BookingConfirmation of(Appointment a) {
			String first = a.getCustomerName().trim().split("\\s+")[0];
			return new BookingConfirmation(a.getReference(), a.getStatus(), a.getService().getName(),
					a.getService().getDurationMinutes(), a.getService().getPrice(), a.getStaff().getFullName(),
					a.getStaff().getTitle(), a.getStartTime(), a.getEndTime(), first);
		}
	}

	/** What staff see on the dashboard. */
	public record AppointmentView(Long id, String reference, AppointmentStatus status, LocalDateTime start,
			LocalDateTime end, Long serviceId, String serviceName, Long staffId, String staffName, String customerName,
			String customerEmail, String customerPhone, String notes, LocalDateTime createdAt,
			LocalDateTime confirmationSentAt, LocalDateTime reminderSentAt) {

		public static AppointmentView of(Appointment a) {
			return new AppointmentView(a.getId(), a.getReference(), a.getStatus(), a.getStartTime(), a.getEndTime(),
					a.getService().getId(), a.getService().getName(), a.getStaff().getId(), a.getStaff().getFullName(),
					a.getCustomerName(), a.getCustomerEmail(), a.getCustomerPhone(), a.getNotes(), a.getCreatedAt(),
					a.getConfirmationSentAt(), a.getReminderSentAt());
		}
	}

	public record StatusChange(@NotNull AppointmentStatus status) {
	}
}
