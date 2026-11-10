package com.bhargavang.caredesk.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * A person customers book with: a doctor, a stylist, a physio.
 */
@Entity
@Table(name = "staff")
public class Staff {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "full_name", nullable = false, length = 120)
	private String fullName;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(length = 160)
	private String email;

	@Column(length = 500)
	private String bio;

	@Column(nullable = false)
	private boolean active = true;

	@ManyToMany
	@JoinTable(name = "staff_service",
			joinColumns = @JoinColumn(name = "staff_id"),
			inverseJoinColumns = @JoinColumn(name = "service_id"))
	private Set<ServiceOffering> services = new HashSet<>();

	@OneToMany(mappedBy = "staff", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<WorkingHours> workingHours = new ArrayList<>();

	protected Staff() {
	}

	public Staff(String fullName, String title, String email, String bio) {
		this.fullName = fullName;
		this.title = title;
		this.email = email;
		this.bio = bio;
	}

	public boolean offers(ServiceOffering service) {
		return services.stream().anyMatch(s -> s.getId().equals(service.getId()));
	}

	public void addWorkingHours(WorkingHours hours) {
		hours.setStaff(this);
		workingHours.add(hours);
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getBio() {
		return bio;
	}

	public void setBio(String bio) {
		this.bio = bio;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Set<ServiceOffering> getServices() {
		return services;
	}

	public List<WorkingHours> getWorkingHours() {
		return workingHours;
	}
}
