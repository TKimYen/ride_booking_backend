package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.CancellationRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancellationRuleRepository extends JpaRepository<CancellationRule, Integer> {

	/** Mốc lớn nhất trước, để tìm mốc đầu tiên khách còn thoả. */
	List<CancellationRule> findByCompanyIdOrderByHoursBeforeDesc(Integer companyId);

	Optional<CancellationRule> findByIdAndCompanyId(Integer id, Integer companyId);

	/** Viết JPQL vì tên suy diễn sẽ hiểu chữ {@code Before} là phép so sánh thời gian. */
	@Query("SELECT r FROM CancellationRule r WHERE r.company.id = :companyId AND r.hoursBefore = :hours")
	Optional<CancellationRule> findForCompanyAtHours(@Param("companyId") Integer companyId, @Param("hours") Integer hours);
}
