package com.bhargavang.caredesk.stats;

import java.time.Clock;
import java.time.LocalDate;

import com.bhargavang.caredesk.config.OpenApiConfig;
import com.bhargavang.caredesk.stats.StatsService.DailyStats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
@Tag(name = "Staff: stats")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StatsController {

	private final StatsService stats;
	private final Clock clock;

	public StatsController(StatsService stats, Clock clock) {
		this.stats = stats;
		this.clock = clock;
	}

	@GetMapping("/daily")
	@Operation(summary = "Appointments per day",
			description = "Defaults to the last 14 days plus the next 7. Cancelled ones are counted separately, not in total.")
	public DailyStats daily(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
		LocalDate today = LocalDate.now(clock);
		return stats.daily(from != null ? from : today.minusDays(13), to != null ? to : today.plusDays(7));
	}
}
