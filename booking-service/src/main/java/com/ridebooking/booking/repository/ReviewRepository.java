package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Review;
import java.util.Collection;
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

	/**
	 * join fetch trip vì mỗi dòng đánh giá cần thông tin chuyến để hiển thị.
	 * Ba truy vấn theo chuyến đều bỏ đánh giá Admin đã ẩn; COALESCE vì cột có thể
	 * NULL trên database chưa chạy 03_booking_db.sql.
	 */
	@Query(value = """
			SELECT r FROM Review r
			JOIN FETCH r.trip t JOIN FETCH t.company JOIN FETCH t.route
			WHERE t.id = :tripId
			  AND COALESCE(r.hidden, false) = false
			ORDER BY r.createdAt DESC, r.id DESC
			""",
			countQuery = """
			SELECT COUNT(r) FROM Review r
			WHERE r.trip.id = :tripId
			  AND COALESCE(r.hidden, false) = false
			""")
	Page<Review> findByTripId(@Param("tripId") Integer tripId, Pageable pageable);

	/** Điểm trung bình và số lượt đánh giá, dùng cho phần tóm tắt trên trang chi tiết. */
	@Query("""
			SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM Review r
			WHERE r.trip.id = :tripId
			  AND COALESCE(r.hidden, false) = false
			""")
	List<Object[]> ratingSummary(@Param("tripId") Integer tripId);

	/** Số đánh gia theo từng mức điểm, để frontend vẽ thanh 5-4-3-2-1 sao. */
	@Query("""
			SELECT r.rating, COUNT(r) FROM Review r
			WHERE r.trip.id = :tripId
			  AND COALESCE(r.hidden, false) = false
			GROUP BY r.rating
			""")
	List<Object[]> ratingBreakdown(@Param("tripId") Integer tripId);

	Page<Review> findByUserId(Integer userId, Pageable pageable);

	/** Đánh giá của một người, gồm cả đánh giá đã bị ẩn để họ biết vì sao không thấy trên trang. */
	@Query(value = """
			SELECT r FROM Review r
			JOIN FETCH r.trip t JOIN FETCH t.company JOIN FETCH t.route
			WHERE r.userId = :userId
			ORDER BY r.createdAt DESC, r.id DESC
			""",
			countQuery = "SELECT COUNT(r) FROM Review r WHERE r.userId = :userId")
	Page<Review> findByUserIdWithTrip(@Param("userId") Integer userId, Pageable pageable);

	boolean existsByBookingId(Integer bookingId);

	/** Đơn nào trong danh sách đã có đánh giá, để trang "Đơn của tôi" ẩn nút đánh giá. */
	@Query("SELECT r.booking.id FROM Review r WHERE r.booking.id IN :bookingIds")
	List<Integer> findReviewedBookingIds(@Param("bookingIds") Collection<Integer> bookingIds);

	// ---- Theo nhà xe ----
	// Nhà xe lấy qua trip.company chứ không qua cột reviews.company_id: cột đó
	// NULL được ở đánh giá seed cũ, còn trip luôn có nhà xe.

	@Query(value = """
			SELECT r FROM Review r
			JOIN FETCH r.trip t JOIN FETCH t.company JOIN FETCH t.route
			WHERE t.company.id = :companyId
			  AND COALESCE(r.hidden, false) = false
			  AND (:rating IS NULL OR r.rating = :rating)
			ORDER BY r.createdAt DESC, r.id DESC
			""",
			countQuery = """
			SELECT COUNT(r) FROM Review r
			WHERE r.trip.company.id = :companyId
			  AND COALESCE(r.hidden, false) = false
			  AND (:rating IS NULL OR r.rating = :rating)
			""")
	Page<Review> findVisibleByCompany(@Param("companyId") Integer companyId,
									  @Param("rating") Integer rating,
									  Pageable pageable);

	@Query("""
			SELECT r.rating, COUNT(r) FROM Review r
			WHERE r.trip.company.id = :companyId
			  AND COALESCE(r.hidden, false) = false
			GROUP BY r.rating
			""")
	List<Object[]> ratingBreakdownByCompany(@Param("companyId") Integer companyId);

	/** Mỗi dòng là [companyId, điểm trung bình, số lượt]; nhà xe chưa có đánh giá thì không có dòng. */
	@Query("""
			SELECT r.trip.company.id, AVG(r.rating), COUNT(r) FROM Review r
			WHERE r.trip.company.id IN :companyIds
			  AND COALESCE(r.hidden, false) = false
			GROUP BY r.trip.company.id
			""")
	List<Object[]> ratingByCompanyIds(@Param("companyIds") Collection<Integer> companyIds);

	// ---- Admin: thấy cả đánh giá đã ẩn ----

	@Query(value = """
			SELECT r FROM Review r
			JOIN FETCH r.trip t JOIN FETCH t.company JOIN FETCH t.route
			WHERE (:companyId IS NULL OR t.company.id = :companyId)
			  AND (:rating IS NULL OR r.rating = :rating)
			  AND (:hidden IS NULL OR COALESCE(r.hidden, false) = :hidden)
			ORDER BY r.createdAt DESC, r.id DESC
			""",
			countQuery = """
			SELECT COUNT(r) FROM Review r
			WHERE (:companyId IS NULL OR r.trip.company.id = :companyId)
			  AND (:rating IS NULL OR r.rating = :rating)
			  AND (:hidden IS NULL OR COALESCE(r.hidden, false) = :hidden)
			""")
	Page<Review> findForAdmin(@Param("companyId") Integer companyId,
							  @Param("rating") Integer rating,
							  @Param("hidden") Boolean hidden,
							  Pageable pageable);
}
