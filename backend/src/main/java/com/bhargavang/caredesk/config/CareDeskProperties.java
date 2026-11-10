package com.bhargavang.caredesk.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under {@code caredesk.*} in application.yml.
 */
@ConfigurationProperties(prefix = "caredesk")
public record CareDeskProperties(
		Clinic clinic,
		Booking booking,
		Jwt jwt,
		Cors cors,
		Mail mail,
		RateLimit rateLimit,
		Seed seed) {

	public record Clinic(String name, String address, String phone, ZoneId timezone) {
	}

	/**
	 * @param blockMinutes    size of the blocks an appointment occupies; service durations must be a multiple
	 * @param slotStepMinutes how far apart the offered start times are
	 * @param minLeadMinutes  how soon from now the earliest bookable slot can start
	 * @param maxDaysAhead    how far ahead customers can book
	 */
	public record Booking(int blockMinutes, int slotStepMinutes, int minLeadMinutes, int maxDaysAhead) {
	}

	/**
	 * @param secret        HMAC key, at least 32 characters. Every app instance must share it.
	 * @param requireSecret refuse to start without a secret (on in the postgres profile, so
	 *                      several instances never end up with different random keys)
	 * @param cookieSecure  mark the session cookie Secure (HTTPS only); browsers allow it on localhost
	 */
	public record Jwt(String secret, Duration ttl, boolean requireSecret, boolean cookieSecure) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

	public record Mail(String mode, String from) {
	}

	/**
	 * Per-IP limits for the endpoints people could abuse. Counters live in memory, so with
	 * several instances each one enforces its own limit (see README for moving them to Redis).
	 */
	public record RateLimit(boolean enabled, int loginAttempts, Duration loginWindow, int bookings,
			Duration bookingWindow, int lookups, Duration lookupWindow, int failedLoginsPerAccount) {
	}

	public record Seed(boolean enabled, String demoEmail, String demoPassword) {
	}
}
