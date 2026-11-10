package com.bhargavang.caredesk.booking;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

	/** Live (not cancelled) appointments of these staff that overlap [from, to). */
	@Query("""
			select a from Appointment a
			where a.staff.id in :staffIds
			  and a.status <> com.bhargavang.caredesk.booking.AppointmentStatus.CANCELLED
			  and a.startTime < :to and a.endTime > :from""")
	List<Appointment> findLiveOverlapping(@Param("staffIds") Collection<Long> staffIds,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Query("""
			select count(a) from Appointment a
			where a.staff.id = :staffId
			  and a.status <> com.bhargavang.caredesk.booking.AppointmentStatus.CANCELLED
			  and a.startTime < :end and a.endTime > :start""")
	long countLiveOverlapping(@Param("staffId") Long staffId,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@Query("""
			select a from Appointment a join fetch a.staff join fetch a.service
			where a.startTime >= :from and a.startTime < :to
			order by a.startTime, a.staff.id""")
	List<Appointment> findWithDetailsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Query("select a from Appointment a join fetch a.staff join fetch a.service where a.id = :id")
	Optional<Appointment> findWithDetailsById(@Param("id") Long id);

	@EntityGraph(attributePaths = { "staff", "service" })
	Optional<Appointment> findByReference(String reference);

	boolean existsByReference(String reference);

	@Query("""
			select a.id from Appointment a
			where a.status = com.bhargavang.caredesk.booking.AppointmentStatus.BOOKED
			  and a.confirmationSentAt is null and a.startTime > :now
			order by a.createdAt""")
	List<Long> findIdsNeedingConfirmation(@Param("now") LocalDateTime now, Limit limit);

	@Query("""
			select a.id from Appointment a
			where a.status = com.bhargavang.caredesk.booking.AppointmentStatus.BOOKED
			  and a.reminderSentAt is null
			  and a.startTime >= :from and a.startTime < :to
			  and a.createdAt < :createdBefore
			order by a.startTime""")
	List<Long> findIdsNeedingReminder(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("createdBefore") LocalDateTime createdBefore, Limit limit);

	/*
	 * Claims make the email jobs safe to run on several instances at once: the conditional
	 * UPDATE succeeds (returns 1) for exactly one of them, and only that one sends the email.
	 */

	@Modifying
	@Query("update Appointment a set a.confirmationSentAt = :now where a.id = :id and a.confirmationSentAt is null")
	int claimConfirmation(@Param("id") Long id, @Param("now") LocalDateTime now);

	@Modifying
	@Query("update Appointment a set a.reminderSentAt = :now where a.id = :id and a.reminderSentAt is null")
	int claimReminder(@Param("id") Long id, @Param("now") LocalDateTime now);

	@Modifying
	@Query("update Appointment a set a.confirmationSentAt = null where a.id = :id")
	int releaseConfirmation(@Param("id") Long id);

	@Modifying
	@Query("update Appointment a set a.reminderSentAt = null where a.id = :id")
	int releaseReminder(@Param("id") Long id);

	@Query("""
			select new com.bhargavang.caredesk.booking.StatusAtTime(a.startTime, a.status) from Appointment a
			where a.startTime >= :from and a.startTime < :to""")
	List<StatusAtTime> findStatusesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
