package com.bhargavang.caredesk.auth;

import java.util.Set;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.util.WebUtils;

/**
 * Reads the JWT from the Authorization header (API clients, Swagger) or, failing that,
 * from the HttpOnly session cookie (the staff dashboard).
 *
 * <p>Cookie auth is the classic CSRF target, so a cookie only counts for an unsafe request
 * (POST, PUT, PATCH, DELETE) when it also carries the {@code X-CareDesk-Client} header.
 * Browsers won't let another site add custom headers without a CORS preflight, which
 * our CORS rules refuse, and SameSite=Strict already keeps the cookie off cross-site requests.
 */
public class CookieOrHeaderTokenResolver implements BearerTokenResolver {

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

	private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

	@Override
	public String resolve(HttpServletRequest request) {
		String fromHeader = headerResolver.resolve(request);
		if (fromHeader != null) {
			return fromHeader;
		}
		if (!request.getRequestURI().startsWith(SessionCookie.PATH + "/")) {
			return null;
		}
		Cookie cookie = WebUtils.getCookie(request, SessionCookie.NAME);
		if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
			return null;
		}
		boolean safe = SAFE_METHODS.contains(request.getMethod());
		if (!safe && request.getHeader(SessionCookie.CLIENT_HEADER) == null) {
			return null;
		}
		return cookie.getValue();
	}
}
