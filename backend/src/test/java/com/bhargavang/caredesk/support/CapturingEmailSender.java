package com.bhargavang.caredesk.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.bhargavang.caredesk.notify.EmailMessage;
import com.bhargavang.caredesk.notify.EmailSender;

/** Records emails instead of sending them, and can be told to fail. */
public class CapturingEmailSender implements EmailSender {

	public final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
	public volatile boolean failing;

	@Override
	public String send(EmailMessage message) {
		if (failing) {
			throw new IllegalStateException("SMTP server unreachable");
		}
		sent.add(message);
		return "SENT";
	}
}
