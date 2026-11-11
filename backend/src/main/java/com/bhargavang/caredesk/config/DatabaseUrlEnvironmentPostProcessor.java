package com.bhargavang.caredesk.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Hosted Postgres providers (Neon, Supabase, Render, Railway) hand out connection strings like
 * {@code postgresql://user:password@host/db?sslmode=require}. The JDBC driver wants
 * {@code jdbc:postgresql://host/db?sslmode=require} with the user and password passed separately,
 * so this splits such a DATABASE_URL into DATABASE_URL, DATABASE_USERNAME and DATABASE_PASSWORD.
 * A jdbc: URL is left alone. The value is never logged, because it contains the password.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

	static final String SOURCE_NAME = "caredeskDatabaseUrl";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Map<String, Object> converted = convert(environment.getProperty("DATABASE_URL"));
		if (!converted.isEmpty()) {
			environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, converted));
		}
	}

	/** Returns the replacement properties, or an empty map if the URL needs no conversion. */
	static Map<String, Object> convert(String url) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
			return result;
		}
		URI uri;
		try {
			uri = URI.create(url.trim());
		}
		catch (IllegalArgumentException e) {
			// Don't echo the URL: it holds the password.
			throw new IllegalStateException("DATABASE_URL isn't a valid postgresql:// URL. "
					+ "Copy the connection string from your database provider again.");
		}
		if (uri.getHost() == null) {
			throw new IllegalStateException("DATABASE_URL has no host name.");
		}

		StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
		if (uri.getPort() > 0) {
			jdbc.append(':').append(uri.getPort());
		}
		jdbc.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/postgres" : uri.getRawPath());
		String query = jdbcQuery(uri.getRawQuery());
		if (!query.isEmpty()) {
			jdbc.append('?').append(query);
		}
		result.put("DATABASE_URL", jdbc.toString());

		String userInfo = uri.getRawUserInfo();
		if (userInfo != null && !userInfo.isEmpty()) {
			int colon = userInfo.indexOf(':');
			result.put("DATABASE_USERNAME", decode(colon < 0 ? userInfo : userInfo.substring(0, colon)));
			if (colon >= 0) {
				result.put("DATABASE_PASSWORD", decode(userInfo.substring(colon + 1)));
			}
		}
		return result;
	}

	/** libpq spells some options differently from the JDBC driver. */
	private static String jdbcQuery(String rawQuery) {
		if (rawQuery == null || rawQuery.isEmpty()) {
			return "";
		}
		StringJoiner joined = new StringJoiner("&");
		for (String pair : rawQuery.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			joined.add(pair.startsWith("channel_binding=") ? "channelBinding=" + pair.substring(16) : pair);
		}
		return joined.toString();
	}

	/** Percent-decoding only: a "+" in a password is a plus sign, not a space. */
	private static String decode(String raw) {
		return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
	}
}
