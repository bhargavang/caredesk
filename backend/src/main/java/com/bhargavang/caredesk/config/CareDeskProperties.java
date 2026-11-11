package com.bhargavang.caredesk.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import com.bhargavang.caredesk.auth.Role;

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
		ClientIp clientIp,
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

	/**
	 * How the visitor's IP is found for rate limits.
	 *
	 * @param trustedHops how many proxies in front of the app append to X-Forwarded-For. 0 uses the
	 *                    connecting address; 1 uses the last entry, written by the one proxy you control.
	 *                    Entries further left are whatever the client sent, so they are never trusted.
	 * @param header      a header your edge sets and overwrites itself (for example CF-Connecting-IP);
	 *                    when present it wins over X-Forwarded-For
	 * @param debug       expose GET /api/public/client-ip, which echoes what the proxy sent, so you can
	 *                    pick the right setting for a new host. Leave it off otherwise.
	 */
	public record ClientIp(int trustedHops, String header, boolean debug) {
	}

	/**
	 * @param enabled    fill an empty database with the demo clinic
	 * @param demoRole   role of the demo login; STAFF on a public demo, so visitors can't edit the catalogue
	 * @param dailyReset regenerate the demo appointments once a day, so a long-running demo always has
	 *                   bookings around today. Only for demos: it deletes every appointment.
	 */
	public record Seed(boolean enabled, String demoEmail, String demoPassword, Role demoRole, boolean dailyReset) {
	}
}
