package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.CancellationRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CancellationRuleRepository extends JpaRepository<CancellationRule, Integer> {

	/** Mốc lớn nhất trước, để tìm mốc đầu tiên khách còn thoả. */
	List<CancellationRule> findByCompanyIdOrderByHoursBeforeDesc(Integer companyId);
}
