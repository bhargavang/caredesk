package com.bhargavang.caredesk.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhargavang.caredesk.auth.Role;
import com.bhargavang.caredesk.common.RateLimiter;
import com.bhargavang.caredesk.support.IntegrationTest;
import com.bhargavang.caredesk.support.TestData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"caredesk.rate-limit.enabled=true",
		"caredesk.rate-limit.login-attempts=3",
		"caredesk.rate-limit.failed-logins-per-account=2",
		"caredesk.rate-limit.bookings=2" })
class RateLimitTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	TestData data;
	@Autowired
	RateLimiter limiter;

	@BeforeEach
	void setUp() {
		limiter.clear();
		data.wipe();
		data.user("desk@clinic.test", Role.STAFF);
	}

	@Test
	void loginAttemptsAreLimitedPerIp() throws Exception {
		for (int i = 0; i < 3; i++) {
			mvc.perform(login("10.0.0.1", "user" + i + "@clinic.test", "wrong")).andExpect(status().isUnauthorized());
		}
		mvc.perform(login("10.0.0.1", "user9@clinic.test", "wrong"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"));
		// Someone else on another network is unaffected.
		mvc.perform(login("10.0.0.99", "desk@clinic.test", TestData.PASSWORD)).andExpect(status().isOk());
	}

	@Test
	void repeatedWrongPasswordsLockTheAccountForAWhile() throws Exception {
		mvc.perform(login("10.0.1.1", "desk@clinic.test", "wrong")).andExpect(status().isUnauthorized());
		mvc.perform(login("10.0.1.2", "desk@clinic.test", "wrong")).andExpect(status().isUnauthorized());
		// Even the right password from a fresh IP waits until the window passes.
		mvc.perform(login("10.0.1.3", "desk@clinic.test", TestData.PASSWORD))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.title").value("Too many attempts"));
	}

	@Test
	void bookingsAreLimitedPerIp() throws Exception {
		for (int i = 0; i < 2; i++) {
			mvc.perform(booking("10.0.2.1")).andExpect(status().isBadRequest());
		}
		mvc.perform(booking("10.0.2.1")).andExpect(status().isTooManyRequests());
	}

	private static MockHttpServletRequestBuilder login(String ip, String email, String password) {
		return post("/api/auth/login")
				.with(r -> {
					r.setRemoteAddr(ip);
					return r;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private static MockHttpServletRequestBuilder booking(String ip) {
		return post("/api/public/appointments")
				.with(r -> {
					r.setRemoteAddr(ip);
					return r;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}");
	}
}
