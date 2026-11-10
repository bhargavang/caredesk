package com.bhargavang.caredesk.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.bhargavang.caredesk.booking.AppointmentDtos.AppointmentView;
import com.bhargavang.caredesk.booking.AppointmentDtos.StatusChange;
import com.bhargavang.caredesk.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/appointments")
@Tag(name = "Staff: appointments")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminAppointmentController {

	private final AppointmentRepository appointments;
	private final BookingService bookings;
	private final Clock clock;

	public AdminAppointmentController(AppointmentRepository appointments, BookingService bookings, Clock clock) {
		this.appointments = appointments;
		this.bookings = bookings;
		this.clock = clock;
	}

	@GetMapping
	@Transactional(readOnly = true)
	@Operation(summary = "Appointments on a day (defaults to today)")
	public List<AppointmentView> forDay(@RequestParam(required = false) LocalDate date) {
		LocalDate day = date != null ? date : LocalDate.now(clock);
		return appointments.findWithDetailsBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
				.stream()
				.map(AppointmentView::of)
				.toList();
	}

	@PatchMapping("/{id}/status")
	@Operation(summary = "Mark an appointment done, no-show or cancelled")
	public AppointmentView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChange change) {
		return AppointmentView.of(bookings.changeStatus(id, change.status()));
	}
}
