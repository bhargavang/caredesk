package com.bhargavang.caredesk.booking;

public enum AppointmentStatus {
	/** Booked and still to happen. */
	BOOKED,
	/** The customer came. */
	DONE,
	/** The customer didn't turn up. */
	NO_SHOW,
	/** Cancelled. The time is free again. */
	CANCELLED
}
