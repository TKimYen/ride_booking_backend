package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Integer> {

	boolean existsByUserIdAndTripId(Integer userId, Integer tripId);

	Optional<Review> findByUserIdAndTripId(Integer userId, Integer tripId);

	/** join fetch trip vì mỗi dòng đánh giá cần thông tin chuyến để hiển thị. */
	@Query("""
			SELECT r FROM Review r
			JOIN FETCH r.trip
			WHERE r.trip.id = :tripId
			ORDER BY r.createdAt DESC, r.id DESC
			""")
	Page<Review> findByTripId(@Param("tripId") Integer tripId, Pageable pageable);

	/** Điểm trung bình và số lượt đánh giá, dùng cho phần tóm tắt trên trang chi tiết. */
	@Query("""
			SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM Review r
			WHERE r.trip.id = :tripId
			""")
	List<Object[]> ratingSummary(@Param("tripId") Integer tripId);

	/** Số đánh gia theo từng mức điểm, để frontend vẽ thanh 5-4-3-2-1 sao. */
	@Query("""
			SELECT r.rating, COUNT(r) FROM Review r
			WHERE r.trip.id = :tripId
			GROUP BY r.rating
			""")
	List<Object[]> ratingBreakdown(@Param("tripId") Integer tripId);

	Page<Review> findByUserId(Integer userId, Pageable pageable);

	@Query("SELECT r FROM Review r JOIN FETCH r.trip WHERE r.userId = :userId ORDER BY r.createdAt DESC")
	Page<Review> findByUserIdWithTrip(@Param("userId") Integer userId, Pageable pageable);
}
