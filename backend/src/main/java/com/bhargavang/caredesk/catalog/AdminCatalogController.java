package com.bhargavang.caredesk.catalog;

import java.util.List;

import com.bhargavang.caredesk.catalog.CatalogDtos.ServiceRequest;
import com.bhargavang.caredesk.catalog.CatalogDtos.ServiceView;
import com.bhargavang.caredesk.catalog.CatalogDtos.StaffAdminView;
import com.bhargavang.caredesk.catalog.CatalogDtos.StaffRequest;
import com.bhargavang.caredesk.catalog.CatalogDtos.WeeklyHoursRequest;
import com.bhargavang.caredesk.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Staff: setup", description = "Services, staff and working hours. Changes need the ADMIN role.")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminCatalogController {

	private final CatalogService catalog;

	public AdminCatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@GetMapping("/services")
	@Operation(summary = "All services, including inactive ones")
	public List<ServiceView> services() {
		return catalog.allServices();
	}

	@PostMapping("/services")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a service")
	public ServiceView createService(@Valid @RequestBody ServiceRequest request) {
		return catalog.createService(request);
	}

	@PutMapping("/services/{id}")
	@Operation(summary = "Update a service")
	public ServiceView updateService(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
		return catalog.updateService(id, request);
	}

	@GetMapping("/staff")
	@Operation(summary = "All staff with their services and working hours")
	public List<StaffAdminView> staff() {
		return catalog.allStaff();
	}

	@PostMapping("/staff")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a staff member")
	public StaffAdminView createStaff(@Valid @RequestBody StaffRequest request) {
		return catalog.createStaff(request);
	}

	@PutMapping("/staff/{id}")
	@Operation(summary = "Update a staff member")
	public StaffAdminView updateStaff(@PathVariable Long id, @Valid @RequestBody StaffRequest request) {
		return catalog.updateStaff(id, request);
	}

	@PutMapping("/staff/{id}/hours")
	@Operation(summary = "Replace a staff member's weekly working hours")
	public StaffAdminView replaceHours(@PathVariable Long id, @Valid @RequestBody WeeklyHoursRequest request) {
		return catalog.replaceWorkingHours(id, request.hours());
	}
}
