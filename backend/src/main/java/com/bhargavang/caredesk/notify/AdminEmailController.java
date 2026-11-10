package com.bhargavang.caredesk.notify;

import java.time.LocalDateTime;
import java.util.List;

import com.bhargavang.caredesk.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/emails")
@Tag(name = "Staff: emails")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminEmailController {

	private final EmailLogRepository emailLog;

	public AdminEmailController(EmailLogRepository emailLog) {
		this.emailLog = emailLog;
	}

	public record EmailView(Long id, Long appointmentId, EmailKind kind, String recipient, String subject, String body,
			String status, LocalDateTime createdAt) {
	}

	@GetMapping
	@Operation(summary = "Recent confirmation and reminder emails")
	public List<EmailView> recent(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		return emailLog.findAllByOrderByCreatedAtDescIdDesc(Limit.of(limit))
				.stream()
				.map(e -> new EmailView(e.getId(), e.getAppointmentId(), e.getKind(), e.getRecipient(), e.getSubject(),
						e.getBody(), e.getStatus(), e.getCreatedAt()))
				.toList();
	}
}
