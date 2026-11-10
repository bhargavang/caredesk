package com.bhargavang.caredesk.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestConfig {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

	@Bean
	@Primary
	CapturingEmailSender capturingEmailSender() {
		return new CapturingEmailSender();
	}

	@Bean
	TestData testData() {
		return new TestData();
	}
}
