package com.bhargavang.caredesk.booking;

/** The chosen time was free when the customer loaded the page but someone else booked it first. */
public class SlotUnavailableException extends RuntimeException {

	public SlotUnavailableException() {
		super("Sorry, that time was just booked by someone else. Please pick another slot.");
	}
}
