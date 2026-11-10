package com.bhargavang.caredesk.booking;

import java.time.LocalDateTime;

import com.bhargavang.caredesk.catalog.Staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A 15-minute block of a staff member's time held by a live appointment.
 * The unique key on (staff_id, block_start) is what makes a double booking
 * impossible at the database level, whatever the application code does.
 */
@Entity
@Table(name = "booked_block",
		uniqueConstraints = @UniqueConstraint(name = "uq_booked_block_staff_start", columnNames = { "staff_id", "block_start" }))
public class BookedBlock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "appointment_id")
	private Appointment appointment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "staff_id")
	private Staff staff;

	@Column(name = "block_start", nullable = false)
	private LocalDateTime blockStart;

	protected BookedBlock() {
	}

	BookedBlock(Appointment appointment, Staff staff, LocalDateTime blockStart) {
		this.appointment = appointment;
		this.staff = staff;
		this.blockStart = blockStart;
	}

	public Long getId() {
		return id;
	}

	public LocalDateTime getBlockStart() {
		return blockStart;
	}
}
