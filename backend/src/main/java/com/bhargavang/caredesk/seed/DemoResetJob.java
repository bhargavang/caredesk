package com.bhargavang.caredesk.seed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * For a demo that stays up for weeks: regenerates the appointments every night, so the
 * dashboard always has bookings around today. Startup does the same check, which covers
 * hosts that sleep when idle. Only active when the demo seed and daily reset are both on.
 */
@Component
@EnableScheduling
@ConditionalOnExpression("${caredesk.seed.enabled:false} and ${caredesk.seed.daily-reset:false}")
class DemoResetJob {

	private final DemoDataSeeder seeder;

	DemoResetJob(DemoDataSeeder seeder) {
		this.seeder = seeder;
	}

	@Scheduled(cron = "${caredesk.seed.reset-cron:0 0 3 * * *}", zone = "${caredesk.clinic.timezone}")
	void refresh() {
		seeder.seedOrRefresh();
	}
}
