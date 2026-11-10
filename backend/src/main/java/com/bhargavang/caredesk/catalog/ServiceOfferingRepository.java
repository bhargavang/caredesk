package com.bhargavang.caredesk.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

	List<ServiceOffering> findByActiveTrueOrderByIdAsc();

	List<ServiceOffering> findAllByOrderByIdAsc();
}
