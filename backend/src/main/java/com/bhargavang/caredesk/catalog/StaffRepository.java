package com.bhargavang.caredesk.catalog;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRepository extends JpaRepository<Staff, Long> {

	@Query("""
			select distinct s from Staff s join s.services svc
			where svc.id = :serviceId and s.active = true
			order by s.id""")
	List<Staff> findActiveOfferingService(@Param("serviceId") Long serviceId);

	List<Staff> findByActiveTrueOrderByIdAsc();

	List<Staff> findAllByOrderByIdAsc();

	/**
	 * Loads a staff member with a row lock (SELECT ... FOR UPDATE). Bookings for the
	 * same person queue up here, so the overlap check that follows sees every earlier booking.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Staff s where s.id = :id")
	Optional<Staff> findByIdForUpdate(@Param("id") Long id);
}
