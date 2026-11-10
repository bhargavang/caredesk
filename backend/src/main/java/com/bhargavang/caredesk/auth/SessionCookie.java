package com.bhargavang.caredesk.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * The staff session cookie. HttpOnly so page scripts (and any injected script) can't read
 * the token, SameSite=Strict so other sites can't make the browser send it, and scoped to
 * /api/admin so public pages never carry it.
 */
public final class SessionCookie {

	public static final String NAME = "caredesk_session";
	public static final String PATH = "/api/admin";
	/** Unsafe requests authenticated by the cookie must also send this header (a CSRF guard). */
	public static final String CLIENT_HEADER = "X-CareDesk-Client";

	private SessionCookie() {
	}

	public static ResponseCookie create(String token, Duration maxAge, boolean secure) {
		return ResponseCookie.from(NAME, token)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Strict")
				.path(PATH)
				.maxAge(maxAge)
				.build();
	}

	public static ResponseCookie clear(boolean secure) {
		return create("", Duration.ZERO, secure);
	}
}
