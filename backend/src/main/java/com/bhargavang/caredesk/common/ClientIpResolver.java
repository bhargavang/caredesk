package com.bhargavang.caredesk.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bhargavang.caredesk.config.CareDeskProperties;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

/**
 * Works out which IP a request came from, for rate limiting.
 *
 * <p>Behind proxies the connecting address is the proxy's, and X-Forwarded-For holds the chain.
 * Each proxy appends the address it saw, so only the entries added by proxies you trust are
 * reliable: counting {@code trustedHops} from the right gives the client. Anything further left
 * was sent by the client and could be made up.
 */
@Component
public class ClientIpResolver {

	/** Longer values can't be IP addresses; capping them keeps rate-limit keys small. */
	private static final int MAX_LENGTH = 64;

	private final int trustedHops;
	private final String header;

	public ClientIpResolver(CareDeskProperties properties) {
		CareDeskProperties.ClientIp settings = properties.clientIp();
		this.trustedHops = settings == null ? 0 : Math.max(0, settings.trustedHops());
		this.header = settings == null || settings.header() == null ? "" : settings.header().trim();
	}

	public String resolve(HttpServletRequest request) {
		if (!header.isEmpty()) {
			String value = firstEntry(request.getHeader(header));
			if (value != null) {
				return cap(value);
			}
		}
		if (trustedHops > 0) {
			List<String> chain = forwardedFor(request);
			if (!chain.isEmpty()) {
				// Fewer entries than proxies means the request skipped one; take the furthest we have.
				return cap(chain.get(Math.max(0, chain.size() - trustedHops)));
			}
		}
		return request.getRemoteAddr();
	}

	static List<String> forwardedFor(HttpServletRequest request) {
		List<String> chain = new ArrayList<>();
		for (String value : Collections.list(request.getHeaders("X-Forwarded-For"))) {
			for (String part : value.split(",")) {
				String entry = part.trim();
				if (!entry.isEmpty()) {
					chain.add(entry);
				}
			}
		}
		return chain;
	}

	private static String firstEntry(String value) {
		if (value == null) {
			return null;
		}
		String first = value.split(",")[0].trim();
		return first.isEmpty() ? null : first;
	}

	private static String cap(String value) {
		return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
	}
}
