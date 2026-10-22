package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Promotion;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Integer> {

	Optional<Promotion> findByCodeIgnoreCase(String code);

	/**
	 * Khoá dòng khi tăng {@code usedCount} lúc đơn chuyển PAID, để hai đơn thanh
	 * toán cùng lúc không vượt {@code usageLimit}.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Promotion p WHERE p.id = :id")
	Optional<Promotion> findByIdForUpdate(@Param("id") Integer id);

	/**
	 * Mã đang hiệu lực áp được cho một chuyến: toàn hệ thống, hoặc đúng nhà xe,
	 * hoặc đúng tuyến. Dùng cho tab Giảm giá.
	 */
	@Query("""
			SELECT p FROM Promotion p
			WHERE p.active = true
			  AND p.startsAt <= :now AND p.endsAt > :now
			  AND (p.usageLimit IS NULL OR p.usedCount < p.usageLimit)
			  AND (p.company IS NULL OR p.company.id = :companyId)
			  AND (p.route IS NULL OR p.route.id = :routeId)
			ORDER BY p.endsAt ASC
			""")
	List<Promotion> findApplicable(@Param("companyId") Integer companyId,
			@Param("routeId") Integer routeId, @Param("now") LocalDateTime now);

	/** Block "Ưu đãi nổi bật" ở Trang chủ. */
	@Query("""
			SELECT p FROM Promotion p
			WHERE p.active = true AND p.featured = true
			  AND p.startsAt <= :now AND p.endsAt > :now
			ORDER BY p.endsAt ASC
			""")
	List<Promotion> findFeatured(@Param("now") LocalDateTime now);
}
