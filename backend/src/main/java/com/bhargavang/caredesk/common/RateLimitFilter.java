package com.bhargavang.caredesk.common;

import java.io.IOException;
import java.time.Duration;

import com.bhargavang.caredesk.config.CareDeskProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP limits on login, booking and lookups, checked before anything else runs.
 * Edge protection (Cloudflare, the host's load balancer) is still the first line against floods.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimiter limiter;
	private final ClientIpResolver clientIp;
	private final CareDeskProperties.RateLimit limits;

	public RateLimitFilter(RateLimiter limiter, ClientIpResolver clientIp, CareDeskProperties properties) {
		this.limiter = limiter;
		this.clientIp = clientIp;
		this.limits = properties.rateLimit();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Rule rule = limits.enabled() ? ruleFor(request) : null;
		if (rule != null) {
			long retryAfter = limiter.tryAcquire(rule.name() + ":" + clientIp.resolve(request), rule.limit(), rule.window());
			if (retryAfter > 0) {
				response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
				response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
				response.setContentType("application/problem+json");
				response.getWriter().write("""
						{"title":"Too many requests","status":429,"detail":"Too many requests from your network. Please wait a little and try again."}""");
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private Rule ruleFor(HttpServletRequest request) {
		String path = request.getRequestURI();
		String method = request.getMethod();
		if ("POST".equals(method) && path.equals("/api/auth/login")) {
			return new Rule("login", limits.loginAttempts(), limits.loginWindow());
		}
		if ("POST".equals(method) && path.equals("/api/public/appointments")) {
			return new Rule("booking", limits.bookings(), limits.bookingWindow());
		}
		if ("GET".equals(method) && (path.equals("/api/public/slots") || path.startsWith("/api/public/appointments/"))) {
			return new Rule("lookup", limits.lookups(), limits.lookupWindow());
		}
		return null;
	}

	private record Rule(String name, int limit, Duration window) {
	}
}
