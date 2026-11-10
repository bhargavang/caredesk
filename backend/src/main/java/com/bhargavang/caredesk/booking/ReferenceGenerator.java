package com.bhargavang.caredesk.booking;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** Booking references like CD-7KQ2MX. No 0/O or 1/I/L, so they're easy to read out on the phone. */
@Component
public class ReferenceGenerator {

	private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

	private final SecureRandom random = new SecureRandom();
	private final AppointmentRepository appointments;

	public ReferenceGenerator(AppointmentRepository appointments) {
		this.appointments = appointments;
	}

	/** A reference not used by any saved appointment. */
	public String next() {
		String reference;
		do {
			reference = random();
		}
		while (appointments.existsByReference(reference));
		return reference;
	}

	public String random() {
		StringBuilder sb = new StringBuilder("CD-");
		for (int i = 0; i < 6; i++) {
			sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return sb.toString();
	}
}
