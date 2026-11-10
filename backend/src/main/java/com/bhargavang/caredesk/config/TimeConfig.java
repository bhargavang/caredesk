package com.bhargavang.caredesk.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All "now" and "today" checks use this clock, set to the clinic's time zone,
 * so the server's own time zone never matters.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	@Bean
	Clock clinicClock(CareDeskProperties properties) {
		return Clock.system(properties.clinic().timezone());
	}
}
