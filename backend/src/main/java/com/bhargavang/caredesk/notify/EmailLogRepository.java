package com.bhargavang.caredesk.notify;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

	List<EmailLog> findAllByOrderByCreatedAtDescIdDesc(Limit limit);

	List<EmailLog> findByAppointmentIdOrderByIdAsc(Long appointmentId);
}
