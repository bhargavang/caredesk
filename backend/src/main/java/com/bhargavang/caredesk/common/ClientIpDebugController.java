package com.bhargavang.caredesk.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Off unless CLIENT_IP_DEBUG=true. Shows the caller what the proxies in front of the app sent,
 * so the client-ip settings can be checked on a new host. It only ever echoes the caller's own request.
 */
@Hidden
@RestController
@ConditionalOnProperty(name = "caredesk.client-ip.debug", havingValue = "true")
class ClientIpDebugController {

	private static final String[] HEADERS = { "X-Forwarded-For", "X-Real-IP", "True-Client-IP", "CF-Connecting-IP",
			"Forwarded", "X-Forwarded-Proto", "X-Forwarded-Host", "Host", "Origin" };

	private final ClientIpResolver resolver;

	ClientIpDebugController(ClientIpResolver resolver) {
		this.resolver = resolver;
	}

	@GetMapping("/api/public/client-ip")
	Map<String, Object> clientIp(HttpServletRequest request) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("resolved", resolver.resolve(request));
		result.put("remoteAddr", request.getRemoteAddr());
		Map<String, String> headers = new LinkedHashMap<>();
		for (String name : HEADERS) {
			String value = String.join(" | ", Collections.list(request.getHeaders(name)));
			if (!value.isEmpty()) {
				headers.put(name, value);
			}
		}
		result.put("headers", headers);
		return result;
	}
}
