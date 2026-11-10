package com.bhargavang.caredesk.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.bhargavang.caredesk.auth.Role;
import com.bhargavang.caredesk.auth.SessionCookie;
import com.bhargavang.caredesk.booking.Appointment;
import com.bhargavang.caredesk.booking.BookingRequest;
import com.bhargavang.caredesk.booking.BookingService;
import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.Staff;
import com.bhargavang.caredesk.support.IntegrationTest;
import com.bhargavang.caredesk.support.MutableClock;
import com.bhargavang.caredesk.support.TestData;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class ApiTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	TestData data;
	@Autowired
	MutableClock clock;
	@Autowired
	BookingService bookings;

	ServiceOffering consult;
	Staff rao;

	@BeforeEach
	void setUp() {
		clock.reset();
		data.wipe();
		consult = data.service("Consultation", 30);
		rao = data.staff("Dr Rao", List.of(consult), "09:00-12:00");
		data.user("admin@clinic.test", Role.ADMIN);
		data.user("desk@clinic.test", Role.STAFF);
	}

	@Test
	void publicCatalogIsOpenAndCacheable() throws Exception {
		mvc.perform(get("/api/public/services"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=300")))
				.andExpect(jsonPath("$[0].name").value("Consultation"))
				.andExpect(jsonPath("$[0].durationMinutes").value(30));
		mvc.perform(get("/api/public/staff").param("serviceId", consult.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].fullName").value("Dr Rao"))
				.andExpect(jsonPath("$[0].email").doesNotExist());
	}

	@Test
	void slotsEndpointListsOnlyFreeTimes() throws Exception {
		bookings.book(new BookingRequest(consult.getId(), rao.getId(), LocalDateTime.of(2030, 3, 5, 9, 0), "A B",
				"a@example.com", null, null));

		mvc.perform(get("/api/public/slots").param("serviceId", consult.getId().toString()).param("date", "2030-03-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$[0].start").value("2030-03-05T09:30:00"))
				.andExpect(jsonPath("$[0].staffName").value("Dr Rao"));
	}

	@Test
	void bookingReturnsAReferenceAndASecondTryGets409() throws Exception {
		String body = bookingJson("2030-03-05T10:00:00", "meera@example.com");

		String created = mvc.perform(post("/api/public/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reference").value(matchesPattern("CD-[A-Z2-9]{6}")))
				.andExpect(jsonPath("$.staffName").value("Dr Rao"))
				.andExpect(jsonPath("$.customerFirstName").value("Meera"))
				.andReturn().getResponse().getContentAsString();

		mvc.perform(post("/api/public/appointments").contentType(MediaType.APPLICATION_JSON)
				.content(bookingJson("2030-03-05T10:00:00", "someone.else@example.com")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Slot taken"));

		String reference = JsonPath.read(created, "$.reference");
		mvc.perform(get("/api/public/appointments/" + reference.toLowerCase()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reference").value(reference))
				.andExpect(content().string(not(containsString("meera@example.com"))));
	}

	@Test
	void invalidBookingsGetFieldErrors() throws Exception {
		mvc.perform(post("/api/public/appointments").contentType(MediaType.APPLICATION_JSON)
				.content(bookingJson("2030-03-05T10:00:00", "not-an-email")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.customerEmail").exists());

		mvc.perform(post("/api/public/appointments").contentType(MediaType.APPLICATION_JSON)
				.content(bookingJson("2030-03-05T13:00:00", "late@example.com")))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void staffEndpointsNeedALogin() throws Exception {
		mvc.perform(get("/api/admin/appointments")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/appointments").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongPasswordIsRejectedWithoutSayingWhichPartWasWrong() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@clinic.test\",\"password\":\"guess\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@clinic.test\",\"password\":\"guess\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
	}

	@Test
	void loginSetsAHardenedCookieAndBothCookieAndBearerWork() throws Exception {
		String setCookie = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ADMIN@clinic.test\",\"password\":\"" + TestData.PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		org.assertj.core.api.Assertions.assertThat(setCookie)
				.startsWith(SessionCookie.NAME + "=")
				.contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/admin");

		String token = login("admin@clinic.test");
		mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("admin@clinic.test"));
		mvc.perform(get("/api/admin/appointments").cookie(new Cookie(SessionCookie.NAME, token)))
				.andExpect(status().isOk());
	}

	@Test
	void cookieAuthenticatedWritesNeedTheClientHeader() throws Exception {
		clock.set(LocalDateTime.of(2030, 3, 4, 7, 0));
		Appointment a = bookings.book(new BookingRequest(consult.getId(), rao.getId(), LocalDateTime.of(2030, 3, 4, 10, 0),
				"A B", "a@example.com", null, null));
		String token = login("desk@clinic.test");
		Cookie cookie = new Cookie(SessionCookie.NAME, token);
		String done = "{\"status\":\"DONE\"}";

		// A forged cross-site form post would carry the cookie (if SameSite didn't stop it) but not the header.
		mvc.perform(patch("/api/admin/appointments/" + a.getId() + "/status").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(done))
				.andExpect(status().isUnauthorized());

		mvc.perform(patch("/api/admin/appointments/" + a.getId() + "/status").cookie(cookie)
				.header(SessionCookie.CLIENT_HEADER, "web")
				.contentType(MediaType.APPLICATION_JSON).content(done))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void frontDeskCanSeeSetupButOnlyAdminsCanChangeIt() throws Exception {
		String newService = "{\"name\":\"Dressing\",\"durationMinutes\":15,\"price\":200}";

		String desk = login("desk@clinic.test");
		mvc.perform(get("/api/admin/staff").header(HttpHeaders.AUTHORIZATION, "Bearer " + desk))
				.andExpect(status().isOk());
		mvc.perform(post("/api/admin/services").header(HttpHeaders.AUTHORIZATION, "Bearer " + desk)
				.contentType(MediaType.APPLICATION_JSON).content(newService))
				.andExpect(status().isForbidden());

		String admin = login("admin@clinic.test");
		mvc.perform(post("/api/admin/services").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content(newService))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Dressing"));
		mvc.perform(post("/api/admin/services").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Odd\",\"durationMinutes\":20,\"price\":200}"))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void workingHoursCanBeReplacedButNotOverlap() throws Exception {
		String admin = login("admin@clinic.test");
		mvc.perform(put("/api/admin/staff/" + rao.getId() + "/hours", admin,
				"{\"hours\":[{\"dayOfWeek\":\"MONDAY\",\"start\":\"09:00\",\"end\":\"12:00\"},"
						+ "{\"dayOfWeek\":\"MONDAY\",\"start\":\"11:00\",\"end\":\"13:00\"}]}"))
				.andExpect(status().isUnprocessableContent());
		mvc.perform(put("/api/admin/staff/" + rao.getId() + "/hours", admin,
				"{\"hours\":[{\"dayOfWeek\":\"SATURDAY\",\"start\":\"10:00\",\"end\":\"12:00\"}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workingHours", hasSize(1)));
		mvc.perform(get("/api/public/slots").param("serviceId", consult.getId().toString()).param("date", "2030-03-09"))
				.andExpect(jsonPath("$", hasSize(4)));
	}

	@Test
	void statsCountAppointmentsPerDay() throws Exception {
		bookings.book(new BookingRequest(consult.getId(), rao.getId(), LocalDateTime.of(2030, 3, 5, 10, 0), "A B",
				"a@example.com", null, null));
		String token = login("desk@clinic.test");

		mvc.perform(get("/api/admin/stats/daily").param("from", "2030-03-04").param("to", "2030-03-10")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days", hasSize(7)))
				.andExpect(jsonPath("$.days[1].date").value("2030-03-05"))
				.andExpect(jsonPath("$.days[1].booked").value(1))
				.andExpect(jsonPath("$.summary.next7Days").value(1));
	}

	@Test
	void responsesCarrySecurityHeaders() throws Exception {
		mvc.perform(get("/api/public/services"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
				.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
	}

	@Test
	void apiDocsArePublic() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("CareDesk API"));
	}

	@Test
	void unknownPathsAreDenied() throws Exception {
		mvc.perform(get("/api/internal/anything")).andExpect(status().isUnauthorized());
	}

	private String login(String email) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder put(String url,
			String token, String json) {
		return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json);
	}

	private String bookingJson(String start, String email) {
		return """
				{"serviceId": %d, "staffId": %d, "start": "%s",
				 "customerName": "Meera Iyer", "customerEmail": "%s", "customerPhone": "+91 98450 12345"}"""
				.formatted(consult.getId(), rao.getId(), start, email);
	}
}
