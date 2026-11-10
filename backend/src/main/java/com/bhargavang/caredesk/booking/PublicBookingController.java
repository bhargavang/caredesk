package com.bhargavang.caredesk.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.bhargavang.caredesk.booking.AppointmentDtos.BookingConfirmation;
import com.bhargavang.caredesk.catalog.CatalogDtos.ServiceView;
import com.bhargavang.caredesk.catalog.CatalogDtos.StaffView;
import com.bhargavang.caredesk.catalog.CatalogService;
import com.bhargavang.caredesk.common.NotFoundException;
import com.bhargavang.caredesk.config.CareDeskProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@Tag(name = "Public booking", description = "Used by the customer booking page. No login needed.")
public class PublicBookingController {

	private static final CacheControl CATALOG_CACHE = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

	private final CatalogService catalog;
	private final SlotService slots;
	private final BookingService bookings;
	private final AppointmentRepository appointments;
	private final CareDeskProperties properties;

	public PublicBookingController(CatalogService catalog, SlotService slots, BookingService bookings,
			AppointmentRepository appointments, CareDeskProperties properties) {
		this.catalog = catalog;
		this.slots = slots;
		this.bookings = bookings;
		this.appointments = appointments;
		this.properties = properties;
	}

	public record ClinicInfo(String name, String address, String phone, String timezone, int maxDaysAhead) {
	}

	@GetMapping("/clinic")
	@Operation(summary = "Clinic name, address and booking window")
	public ResponseEntity<ClinicInfo> clinic() {
		CareDeskProperties.Clinic c = properties.clinic();
		return ResponseEntity.ok()
				.cacheControl(CATALOG_CACHE)
				.body(new ClinicInfo(c.name(), c.address(), c.phone(), c.timezone().getId(),
						properties.booking().maxDaysAhead()));
	}

	@GetMapping("/services")
	@Operation(summary = "Bookable services")
	public ResponseEntity<List<ServiceView>> services() {
		return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(catalog.activeServices());
	}

	@GetMapping("/staff")
	@Operation(summary = "Staff, optionally only those who offer a service")
	public ResponseEntity<List<StaffView>> staff(@RequestParam(required = false) Long serviceId) {
		return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(catalog.activeStaff(serviceId));
	}

	@GetMapping("/slots")
	@Operation(summary = "Free start times for a service on a date",
			description = "Only free slots are returned. Leave staffId out for \"anyone available\".")
	public ResponseEntity<List<FreeSlot>> freeSlots(@RequestParam Long serviceId, @RequestParam LocalDate date,
			@RequestParam(required = false) Long staffId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(slots.findFreeSlots(serviceId, date, staffId));
	}

	@PostMapping("/appointments")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Book a slot", description = "Returns 409 if someone else booked the time first.")
	public BookingConfirmation book(@Valid @RequestBody BookingRequest request) {
		return BookingConfirmation.of(bookings.book(request));
	}

	@GetMapping("/appointments/{reference}")
	@Transactional(readOnly = true)
	@Operation(summary = "Look up a booking by its reference")
	public BookingConfirmation lookup(@PathVariable String reference) {
		return appointments.findByReference(reference.trim().toUpperCase())
				.map(BookingConfirmation::of)
				.orElseThrow(() -> new NotFoundException("No booking with that reference"));
	}
}
