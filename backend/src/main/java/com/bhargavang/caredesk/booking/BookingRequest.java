package com.bhargavang.caredesk.booking;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookingRequest(
		@NotNull Long serviceId,
		@NotNull Long staffId,
		@NotNull LocalDateTime start,
		@NotBlank @Size(max = 120) String customerName,
		@NotBlank @Email @Size(max = 160) String customerEmail,
		@Size(max = 32) @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "can only contain digits, spaces and + ( ) -") String customerPhone,
		@Size(max = 500) String notes) {
}
