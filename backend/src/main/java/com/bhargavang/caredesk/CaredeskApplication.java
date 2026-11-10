package com.bhargavang.caredesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CaredeskApplication {

	public static void main(String[] args) {
		SpringApplication.run(CaredeskApplication.class, args);
	}

}
