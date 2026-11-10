package com.bhargavang.caredesk.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.bhargavang.caredesk.config.CareDeskProperties;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived, signed JWTs. Nothing is stored server-side, so any instance can check them. */
@Service
public class TokenService {

	public static final String ISSUER = "caredesk";

	private final JwtEncoder encoder;
	private final CareDeskProperties.Jwt settings;
	private final Clock clock;

	public TokenService(JwtEncoder encoder, CareDeskProperties properties, Clock clock) {
		this.encoder = encoder;
		this.settings = properties.jwt();
		this.clock = clock;
	}

	public IssuedToken issue(AppUser user) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(settings.ttl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(user.getEmail())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.claim("name", user.getDisplayName())
				.claim("roles", List.of(user.getRole().name()))
				.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}
}
