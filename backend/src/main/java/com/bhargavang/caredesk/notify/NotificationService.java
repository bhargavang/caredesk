package com.bhargavang.caredesk.notify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.bhargavang.caredesk.booking.Appointment;
import com.bhargavang.caredesk.booking.AppointmentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends confirmation and day-before reminder emails. Called by {@link EmailJobs}.
 *
 * <p>Each email is claimed with a conditional UPDATE before it is sent, so running several
 * app instances never sends the same email twice. If sending fails, the claim is released
 * and the next run tries again.
 */
@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
	private static final Limit BATCH = Limit.of(100);

	private final AppointmentRepository appointments;
	private final EmailLogRepository emailLog;
	private final EmailComposer composer;
	private final EmailSender sender;
	private final TransactionTemplate tx;
	private final Clock clock;

	public NotificationService(AppointmentRepository appointments, EmailLogRepository emailLog, EmailComposer composer,
			EmailSender sender, TransactionTemplate tx, Clock clock) {
		this.appointments = appointments;
		this.emailLog = emailLog;
		this.composer = composer;
		this.sender = sender;
		this.tx = tx;
		this.clock = clock;
	}

	/** Confirmation for every upcoming booking that hasn't had one yet. Returns how many went out. */
	public int sendPendingConfirmations() {
		LocalDateTime now = LocalDateTime.now(clock);
		return deliverAll(appointments.findIdsNeedingConfirmation(now, BATCH), EmailKind.CONFIRMATION, now);
	}

	/**
	 * Reminder for tomorrow's bookings. Bookings made today are skipped: their confirmation
	 * went out moments ago, so a reminder would just be noise.
	 */
	public int sendDayBeforeReminders() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate tomorrow = now.toLocalDate().plusDays(1);
		List<Long> ids = appointments.findIdsNeedingReminder(tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay(),
				now.toLocalDate().atStartOfDay(), BATCH);
		return deliverAll(ids, EmailKind.REMINDER, now);
	}

	private int deliverAll(List<Long> ids, EmailKind kind, LocalDateTime now) {
		int sent = 0;
		for (Long id : ids) {
			if (Boolean.TRUE.equals(tx.execute(status -> deliver(id, kind, now)))) {
				sent++;
			}
		}
		if (sent > 0) {
			log.info("Sent {} {} email(s)", sent, kind.name().toLowerCase());
		}
		return sent;
	}

	private boolean deliver(Long id, EmailKind kind, LocalDateTime now) {
		int claimed = kind == EmailKind.CONFIRMATION ? appointments.claimConfirmation(id, now)
				: appointments.claimReminder(id, now);
		if (claimed == 0) {
			return false; // another instance got there first
		}
		Appointment appointment = appointments.findWithDetailsById(id).orElseThrow();
		EmailMessage message = composer.compose(kind, appointment);
		try {
			String status = sender.send(message);
			emailLog.save(new EmailLog(id, kind, message, status, null, now));
			return true;
		}
		catch (RuntimeException e) {
			log.warn("Couldn't send {} email for appointment {}: {}", kind, id, e.getMessage());
			if (kind == EmailKind.CONFIRMATION) {
				appointments.releaseConfirmation(id);
			}
			else {
				appointments.releaseReminder(id);
			}
			emailLog.save(new EmailLog(id, kind, message, "FAILED", e.getMessage(), now));
			return false;
		}
	}
}
