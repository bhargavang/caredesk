package com.bhargavang.caredesk.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import com.bhargavang.caredesk.catalog.CatalogDtos.HoursRequest;
import com.bhargavang.caredesk.catalog.CatalogDtos.ServiceRequest;
import com.bhargavang.caredesk.catalog.CatalogDtos.ServiceView;
import com.bhargavang.caredesk.catalog.CatalogDtos.StaffAdminView;
import com.bhargavang.caredesk.catalog.CatalogDtos.StaffRequest;
import com.bhargavang.caredesk.catalog.CatalogDtos.StaffView;
import com.bhargavang.caredesk.common.BusinessRuleException;
import com.bhargavang.caredesk.common.NotFoundException;
import com.bhargavang.caredesk.config.CareDeskProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

	private final ServiceOfferingRepository services;
	private final StaffRepository staff;
	private final int blockMinutes;

	public CatalogService(ServiceOfferingRepository services, StaffRepository staff, CareDeskProperties properties) {
		this.services = services;
		this.staff = staff;
		this.blockMinutes = properties.booking().blockMinutes();
	}

	@Transactional(readOnly = true)
	public List<ServiceView> activeServices() {
		return services.findByActiveTrueOrderByIdAsc().stream().map(ServiceView::of).toList();
	}

	@Transactional(readOnly = true)
	public List<ServiceView> allServices() {
		return services.findAllByOrderByIdAsc().stream().map(ServiceView::of).toList();
	}

	@Transactional(readOnly = true)
	public List<StaffView> activeStaff(Long serviceId) {
		List<Staff> people = serviceId == null ? staff.findByActiveTrueOrderByIdAsc()
				: staff.findActiveOfferingService(serviceId);
		return people.stream().map(StaffView::of).toList();
	}

	@Transactional(readOnly = true)
	public List<StaffAdminView> allStaff() {
		return staff.findAllByOrderByIdAsc().stream().map(StaffAdminView::of).toList();
	}

	@Transactional
	public ServiceView createService(ServiceRequest request) {
		checkDuration(request.durationMinutes());
		ServiceOffering service = new ServiceOffering(request.name().trim(), request.description(),
				request.durationMinutes(), request.price());
		if (request.active() != null) {
			service.setActive(request.active());
		}
		return ServiceView.of(services.save(service));
	}

	@Transactional
	public ServiceView updateService(Long id, ServiceRequest request) {
		checkDuration(request.durationMinutes());
		ServiceOffering service = services.findById(id).orElseThrow(() -> new NotFoundException("Service not found"));
		service.setName(request.name().trim());
		service.setDescription(request.description());
		service.setDurationMinutes(request.durationMinutes());
		service.setPrice(request.price());
		if (request.active() != null) {
			service.setActive(request.active());
		}
		return ServiceView.of(service);
	}

	@Transactional
	public StaffAdminView createStaff(StaffRequest request) {
		Staff person = new Staff(request.fullName().trim(), request.title().trim(), request.email(), request.bio());
		applyStaff(person, request);
		return StaffAdminView.of(staff.save(person));
	}

	@Transactional
	public StaffAdminView updateStaff(Long id, StaffRequest request) {
		Staff person = staff.findById(id).orElseThrow(() -> new NotFoundException("Staff member not found"));
		person.setFullName(request.fullName().trim());
		person.setTitle(request.title().trim());
		person.setEmail(request.email());
		person.setBio(request.bio());
		applyStaff(person, request);
		return StaffAdminView.of(person);
	}

	/** Replaces a staff member's whole week. Changes only affect slots offered from now on. */
	@Transactional
	public StaffAdminView replaceWorkingHours(Long staffId, List<HoursRequest> hours) {
		Staff person = staff.findById(staffId).orElseThrow(() -> new NotFoundException("Staff member not found"));
		List<HoursRequest> sorted = new ArrayList<>(hours);
		sorted.sort(Comparator.comparing(HoursRequest::dayOfWeek).thenComparing(HoursRequest::start));
		for (int i = 0; i < sorted.size(); i++) {
			HoursRequest h = sorted.get(i);
			if (!h.start().isBefore(h.end())) {
				throw new BusinessRuleException("Working hours must end after they start (" + h.dayOfWeek() + ")");
			}
			if (i > 0 && sorted.get(i - 1).dayOfWeek() == h.dayOfWeek() && sorted.get(i - 1).end().isAfter(h.start())) {
				throw new BusinessRuleException("Working hours overlap on " + h.dayOfWeek());
			}
		}
		person.getWorkingHours().clear();
		sorted.forEach(h -> person.addWorkingHours(new WorkingHours(h.dayOfWeek(), h.start(), h.end())));
		return StaffAdminView.of(staff.saveAndFlush(person));
	}

	private void applyStaff(Staff person, StaffRequest request) {
		if (request.active() != null) {
			person.setActive(request.active());
		}
		List<ServiceOffering> offered = services.findAllById(request.serviceIds());
		if (offered.size() != request.serviceIds().size()) {
			throw new NotFoundException("One or more services don't exist");
		}
		person.getServices().clear();
		person.getServices().addAll(new HashSet<>(offered));
	}

	private void checkDuration(int minutes) {
		if (minutes % blockMinutes != 0) {
			throw new BusinessRuleException("Duration must be a multiple of " + blockMinutes + " minutes");
		}
	}
}
