package com.bhargavang.caredesk.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default sender: prints the email to the log instead of sending it. */
@Component
@ConditionalOnProperty(name = "caredesk.mail.mode", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public String send(EmailMessage message) {
		log.info("Email (log mode, not sent)\n  To: {}\n  Subject: {}\n\n{}", message.to(), message.subject(), message.body());
		return "LOGGED";
	}
}
