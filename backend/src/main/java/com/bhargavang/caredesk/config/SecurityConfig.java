package com.bhargavang.caredesk.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.bhargavang.caredesk.auth.CookieOrHeaderTokenResolver;
import com.bhargavang.caredesk.auth.TokenService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	SecurityFilterChain api(HttpSecurity http) throws Exception {
		http
				// No server sessions: every request carries its JWT, so any instance can serve it.
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// CSRF is covered by the SameSite=Strict cookie plus the custom header check in
				// CookieOrHeaderTokenResolver; header-token clients aren't exposed to CSRF at all.
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.headers(h -> h
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
										+ "img-src 'self' data:; object-src 'none'; base-uri 'self'; "
										+ "frame-ancestors 'none'; form-action 'self'"))
						.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
						.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
						.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
								"camera=(), microphone=(), geolocation=(), payment=()")))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/public/**", "/api/auth/login", "/api/auth/logout").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers("/actuator/health/**", "/error").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyRole("ADMIN", "STAFF")
						.requestMatchers("/api/admin/services/**", "/api/admin/staff/**").hasRole("ADMIN")
						.requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "STAFF")
						.anyRequest().denyAll())
				.oauth2ResourceServer(o -> o
						.bearerTokenResolver(new CookieOrHeaderTokenResolver())
						.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// BCrypt by default, with the {id} prefix so the algorithm can be upgraded later.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	SecretKey jwtSigningKey(CareDeskProperties properties) {
		String secret = properties.jwt().secret();
		if (secret == null || secret.isBlank()) {
			if (properties.jwt().requireSecret()) {
				throw new IllegalStateException(
						"JWT_SECRET must be set (32+ characters). All instances need the same value.");
			}
			log.warn("JWT_SECRET is not set, so a random signing key is used for this run. "
					+ "Staff will need to log in again after a restart. Set JWT_SECRET before hosting.");
			byte[] random = new byte[32];
			new SecureRandom().nextBytes(random);
			return new SecretKeySpec(random, "HmacSHA256");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET must be at least 32 characters long.");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(TokenService.ISSUER)));
		return decoder;
	}

	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("roles");
		roles.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(roles);
		return converter;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CareDeskProperties properties) {
		// The dashboard is served from the same origin (proxy), so CORS only matters for
		// other front ends calling the API with a bearer token. Cookies are never sent cross-site.
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.cors().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}
}
