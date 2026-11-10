package com.bhargavang.caredesk.notify;

import com.bhargavang.caredesk.config.CareDeskProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real email over SMTP. Turn on with MAIL_MODE=smtp and the standard
 * SPRING_MAIL_HOST / PORT / USERNAME / PASSWORD settings (Resend, Brevo, SES, Gmail...).
 */
@Component
@ConditionalOnProperty(name = "caredesk.mail.mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpEmailSender(JavaMailSender mailSender, CareDeskProperties properties) {
		this.mailSender = mailSender;
		this.from = properties.mail().from();
	}

	@Override
	public String send(EmailMessage message) {
		SimpleMailMessage mail = new SimpleMailMessage();
		mail.setFrom(from);
		mail.setTo(message.to());
		mail.setSubject(message.subject());
		mail.setText(message.body());
		mailSender.send(mail);
		return "SENT";
	}
}
