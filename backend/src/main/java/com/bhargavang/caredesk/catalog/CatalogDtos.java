package com.bhargavang.caredesk.catalog;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for services, staff and working hours. */
public final class CatalogDtos {

	private CatalogDtos() {
	}

	public record ServiceView(Long id, String name, String description, int durationMinutes, BigDecimal price,
			boolean active) {

		static ServiceView of(ServiceOffering s) {
			return new ServiceView(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getPrice(),
					s.isActive());
		}
	}

	public record ServiceRequest(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 500) String description,
			@NotNull @Min(5) @Max(480) Integer durationMinutes,
			@NotNull @DecimalMin("0.00") BigDecimal price,
			Boolean active) {
	}

	public record HoursView(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {

		static List<HoursView> of(List<WorkingHours> hours) {
			return hours.stream()
					.sorted(Comparator.comparing(WorkingHours::getDayOfWeek).thenComparing(WorkingHours::getStartTime))
					.map(h -> new HoursView(h.getDayOfWeek(), h.getStartTime(), h.getEndTime()))
					.toList();
		}
	}

	public record HoursRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime start, @NotNull LocalTime end) {
	}

	/** Public view: no email address. */
	public record StaffView(Long id, String fullName, String title, String bio, List<Long> serviceIds) {

		static StaffView of(Staff s) {
			return new StaffView(s.getId(), s.getFullName(), s.getTitle(), s.getBio(), idsOf(s));
		}
	}

	public record StaffAdminView(Long id, String fullName, String title, String email, String bio, boolean active,
			List<Long> serviceIds, List<HoursView> workingHours) {

		static StaffAdminView of(Staff s) {
			return new StaffAdminView(s.getId(), s.getFullName(), s.getTitle(), s.getEmail(), s.getBio(), s.isActive(),
					idsOf(s), HoursView.of(s.getWorkingHours()));
		}
	}

	public record StaffRequest(
			@NotBlank @Size(max = 120) String fullName,
			@NotBlank @Size(max = 120) String title,
			@Email @Size(max = 160) String email,
			@Size(max = 500) String bio,
			Boolean active,
			@NotNull Set<Long> serviceIds) {
	}

	public record WeeklyHoursRequest(@NotNull @Size(max = 28) List<@Valid HoursRequest> hours) {
	}

	private static List<Long> idsOf(Staff s) {
		return s.getServices().stream().map(ServiceOffering::getId).sorted().toList();
	}
}
