package com.bhargavang.caredesk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

	@Test
	void splitsAProviderConnectionStringIntoJdbcUrlUserAndPassword() {
		Map<String, Object> result = DatabaseUrlEnvironmentPostProcessor.convert(
				"postgresql://neondb_owner:s3cr%40t+pw@ep-quiet-sky-123.ap-southeast-1.aws.neon.tech/neondb"
						+ "?sslmode=require&channel_binding=require");

		assertThat(result).containsEntry("DATABASE_URL",
				"jdbc:postgresql://ep-quiet-sky-123.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channelBinding=require")
				.containsEntry("DATABASE_USERNAME", "neondb_owner")
				.containsEntry("DATABASE_PASSWORD", "s3cr@t+pw");
	}

	@Test
	void keepsThePortAndAcceptsThePostgresScheme() {
		Map<String, Object> result = DatabaseUrlEnvironmentPostProcessor.convert("postgres://app:pw@db.internal:6543/clinic");
		assertThat(result).containsEntry("DATABASE_URL", "jdbc:postgresql://db.internal:6543/clinic")
				.containsEntry("DATABASE_USERNAME", "app")
				.containsEntry("DATABASE_PASSWORD", "pw");
	}

	@Test
	void leavesJdbcUrlsAndMissingValuesAlone() {
		assertThat(DatabaseUrlEnvironmentPostProcessor.convert("jdbc:postgresql://localhost:5432/caredesk")).isEmpty();
		assertThat(DatabaseUrlEnvironmentPostProcessor.convert(null)).isEmpty();
	}

	@Test
	void aBrokenUrlFailsWithoutEchoingThePassword() {
		assertThatThrownBy(() -> DatabaseUrlEnvironmentPostProcessor.convert("postgresql://u:pa ss@host/db"))
				.isInstanceOf(IllegalStateException.class)
				.message().doesNotContain("pa ss");
	}

	@Test
	void overridesTheDatabaseVariablesInTheEnvironment() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("DATABASE_URL", "postgresql://app:pw@db.example.com/clinic?sslmode=require")
				.withProperty("DATABASE_USERNAME", "ignored");
		new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment, null);

		assertThat(environment.getProperty("DATABASE_URL")).isEqualTo("jdbc:postgresql://db.example.com/clinic?sslmode=require");
		assertThat(environment.getProperty("DATABASE_USERNAME")).isEqualTo("app");
		assertThat(environment.getProperty("DATABASE_PASSWORD")).isEqualTo("pw");
	}
}
