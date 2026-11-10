package com.bhargavang.caredesk.notify;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A record of every email the app sent (or logged), shown on the dashboard. */
@Entity
@Table(name = "email_log")
public class EmailLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "appointment_id")
	private Long appointmentId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 16)
	private EmailKind kind;

	@Column(nullable = false, length = 160)
	private String recipient;

	@Column(nullable = false, length = 200)
	private String subject;

	@Column(nullable = false, length = 4000)
	private String body;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(length = 500)
	private String error;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected EmailLog() {
	}

	public EmailLog(Long appointmentId, EmailKind kind, EmailMessage message, String status, String error,
			LocalDateTime createdAt) {
		this.appointmentId = appointmentId;
		this.kind = kind;
		this.recipient = message.to();
		this.subject = message.subject();
		this.body = message.body().length() > 4000 ? message.body().substring(0, 4000) : message.body();
		this.status = status;
		this.error = error == null || error.length() <= 500 ? error : error.substring(0, 500);
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getAppointmentId() {
		return appointmentId;
	}

	public EmailKind getKind() {
		return kind;
	}

	public String getRecipient() {
		return recipient;
	}

	public String getSubject() {
		return subject;
	}

	public String getBody() {
		return body;
	}

	public String getStatus() {
		return status;
	}

	public String getError() {
		return error;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
