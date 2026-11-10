package com.bhargavang.caredesk.booking;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.bhargavang.caredesk.catalog.ServiceOffering;
import com.bhargavang.caredesk.catalog.Staff;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "appointment")
public class Appointment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 12, unique = true)
	private String reference;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "staff_id")
	private Staff staff;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_id")
	private ServiceOffering service;

	@Column(name = "start_time", nullable = false)
	private LocalDateTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalDateTime endTime;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 16)
	private AppointmentStatus status = AppointmentStatus.BOOKED;

	@Column(name = "customer_name", nullable = false, length = 120)
	private String customerName;

	@Column(name = "customer_email", nullable = false, length = 160)
	private String customerEmail;

	@Column(name = "customer_phone", length = 32)
	private String customerPhone;

	@Column(length = 500)
	private String notes;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "confirmation_sent_at")
	private LocalDateTime confirmationSentAt;

	@Column(name = "reminder_sent_at")
	private LocalDateTime reminderSentAt;

	@OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<BookedBlock> blocks = new ArrayList<>();

	protected Appointment() {
	}

	public Appointment(String reference, Staff staff, ServiceOffering service, LocalDateTime startTime,
			String customerName, String customerEmail, String customerPhone, String notes, LocalDateTime createdAt) {
		this.reference = reference;
		this.staff = staff;
		this.service = service;
		this.startTime = startTime;
		this.endTime = startTime.plusMinutes(service.getDurationMinutes());
		this.customerName = customerName;
		this.customerEmail = customerEmail;
		this.customerPhone = customerPhone;
		this.notes = notes;
		this.createdAt = createdAt;
	}

	/**
	 * Claims every block between start and end for this staff member. Saving the
	 * appointment then fails on the unique key if any block is already taken.
	 */
	public void occupyBlocks(int blockMinutes) {
		blocks.clear();
		for (LocalDateTime t = startTime; t.isBefore(endTime); t = t.plusMinutes(blockMinutes)) {
			blocks.add(new BookedBlock(this, staff, t));
		}
	}

	public void cancel() {
		status = AppointmentStatus.CANCELLED;
		blocks.clear();
	}

	public boolean isLive() {
		return status != AppointmentStatus.CANCELLED;
	}

	public Long getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public Staff getStaff() {
		return staff;
	}

	public ServiceOffering getService() {
		return service;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public LocalDateTime getEndTime() {
		return endTime;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public void setStatus(AppointmentStatus status) {
		this.status = status;
	}

	public String getCustomerName() {
		return customerName;
	}

	public String getCustomerEmail() {
		return customerEmail;
	}

	public String getCustomerPhone() {
		return customerPhone;
	}

	public String getNotes() {
		return notes;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getConfirmationSentAt() {
		return confirmationSentAt;
	}

	public void setConfirmationSentAt(LocalDateTime confirmationSentAt) {
		this.confirmationSentAt = confirmationSentAt;
	}

	public LocalDateTime getReminderSentAt() {
		return reminderSentAt;
	}

	public void setReminderSentAt(LocalDateTime reminderSentAt) {
		this.reminderSentAt = reminderSentAt;
	}

	public List<BookedBlock> getBlocks() {
		return blocks;
	}
}
