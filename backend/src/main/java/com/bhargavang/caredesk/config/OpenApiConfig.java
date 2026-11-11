package com.bhargavang.caredesk.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
		title = "CareDesk API",
		version = "v1",
		description = """
				Appointment booking for clinics and salons.

				Public endpoints (services, free slots, booking) need no login.
				Staff endpoints need a JWT: call POST /api/auth/login, then click Authorize and paste the token."""),
		// Relative, so "Try it out" calls whichever address the docs were opened on, even through a proxy.
		servers = @Server(url = "/"))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

	public static final String BEARER = "bearerAuth";
}
