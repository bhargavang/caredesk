package com.bhargavang.caredesk.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A clock tests can move. Starts on Monday 4 March 2030, 08:00 in Bengaluru. */
public class MutableClock extends Clock {

	public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
	public static final LocalDateTime START = LocalDateTime.of(2030, 3, 4, 8, 0);

	private volatile Instant instant = START.atZone(ZONE).toInstant();

	public void set(LocalDateTime localTime) {
		this.instant = localTime.atZone(ZONE).toInstant();
	}

	public void reset() {
		set(START);
	}

	@Override
	public ZoneId getZone() {
		return ZONE;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(instant, zone);
	}

	@Override
	public Instant instant() {
		return instant;
	}
}
