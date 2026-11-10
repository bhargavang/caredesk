package com.bhargavang.caredesk.catalog;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

	List<WorkingHours> findByStaffIdInAndDayOfWeek(Collection<Long> staffIds, DayOfWeek dayOfWeek);
}
