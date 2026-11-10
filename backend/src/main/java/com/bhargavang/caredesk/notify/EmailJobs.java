package com.bhargavang.caredesk.notify;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Background jobs. Confirmations go out within about 30 seconds of booking; reminders are
 * checked hourly, so a server that was asleep (free hosting) catches up when it wakes.
 * Safe to run on every instance; see {@link NotificationService}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "caredesk.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class EmailJobs {

	private final NotificationService notifications;

	public EmailJobs(NotificationService notifications) {
		this.notifications = notifications;
	}

	@Scheduled(fixedDelayString = "${caredesk.jobs.confirmation-delay}", initialDelayString = "PT10S")
	void confirmations() {
		notifications.sendPendingConfirmations();
	}

	@Scheduled(fixedDelayString = "${caredesk.jobs.reminder-delay}", initialDelayString = "${caredesk.jobs.reminder-initial-delay}")
	void reminders() {
		notifications.sendDayBeforeReminders();
	}
}
