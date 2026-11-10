package com.bhargavang.caredesk.notify;

/** Sends one email. Implementations: {@link LoggingEmailSender} (default) and {@link SmtpEmailSender}. */
public interface EmailSender {

	/**
	 * @return the status to record, e.g. "LOGGED" or "SENT"
	 */
	String send(EmailMessage message);
}
