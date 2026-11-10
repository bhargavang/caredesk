package com.bhargavang.caredesk.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.bhargavang.caredesk.auth.TokenService.IssuedToken;
import com.bhargavang.caredesk.common.RateLimiter;
import com.bhargavang.caredesk.common.TooManyRequestsException;
import com.bhargavang.caredesk.config.CareDeskProperties;
import com.bhargavang.caredesk.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auth")
public class AuthController {

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokens;
	private final RateLimiter rateLimiter;
	private final CareDeskProperties properties;
	/** Compared against when the email is unknown, so both paths take about the same time. */
	private final String dummyHash;

	public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
			RateLimiter rateLimiter, CareDeskProperties properties) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.rateLimiter = rateLimiter;
		this.properties = properties;
		this.dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	public record LoginRequest(@NotBlank @Size(max = 160) String email, @NotBlank @Size(max = 128) String password) {
	}

	public record UserView(String email, String name, List<String> roles) {
	}

	public record LoginResponse(UserView user, Instant expiresAt, String accessToken) {
	}

	@PostMapping("/api/auth/login")
	@Operation(summary = "Staff login",
			description = "Sets an HttpOnly session cookie for the dashboard and also returns the token for API clients.")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String accountKey = "login-failures:" + email;
		CareDeskProperties.RateLimit limits = properties.rateLimit();
		if (limits.enabled() && rateLimiter.isOverLimit(accountKey, limits.failedLoginsPerAccount(), limits.loginWindow())) {
			throw new TooManyRequestsException("Too many failed logins for this account. Try again in a few minutes.",
					limits.loginWindow().toSeconds());
		}

		AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
		boolean ok = passwordEncoder.matches(request.password(), user != null ? user.getPasswordHash() : dummyHash);
		if (user == null || !ok) {
			if (limits.enabled()) {
				rateLimiter.record(accountKey, limits.loginWindow());
			}
			throw new BadCredentialsException("Bad credentials");
		}
		rateLimiter.reset(accountKey);

		IssuedToken token = tokens.issue(user);
		Duration maxAge = properties.jwt().ttl();
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE,
						SessionCookie.create(token.value(), maxAge, properties.jwt().cookieSecure()).toString())
				.body(new LoginResponse(new UserView(user.getEmail(), user.getDisplayName(), List.of(user.getRole().name())),
						token.expiresAt(), token.value()));
	}

	@PostMapping("/api/auth/logout")
	@Operation(summary = "Clear the session cookie")
	public ResponseEntity<Void> logout() {
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, SessionCookie.clear(properties.jwt().cookieSecure()).toString())
				.build();
	}

	@GetMapping("/api/admin/me")
	@Operation(summary = "Who is logged in")
	@SecurityRequirement(name = OpenApiConfig.BEARER)
	public UserView me(@AuthenticationPrincipal Jwt jwt) {
		return new UserView(jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsStringList("roles"));
	}
}
