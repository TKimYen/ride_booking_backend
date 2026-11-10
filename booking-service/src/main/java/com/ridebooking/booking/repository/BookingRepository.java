package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.TripStatus;
import java.time.LocalDateTime;
import com.ridebooking.booking.util.LikePattern;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Integer>, JpaSpecificationExecutor<Booking> {

	/** Trang Quản lý Đơn hàng của Admin có bộ lọc [4.5]; nạp sẵn chuyến để không sinh N+1. */
	@Override
	@EntityGraph(attributePaths = { "trip", "trip.route", "trip.route.origin", "trip.route.destination",
			"trip.company", "pickupPoint", "dropoffPoint", "promotion" })
	Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

	/**
	 * Bộ lọc cho Admin. Mọi tham số đều tuỳ chọn (null = bỏ qua).
	 *
	 * @param keyword  khớp một phần, không phân biệt hoa thường, với mã vé, tên, SĐT hoặc email liên hệ
	 * @param from     đơn tạo từ thời điểm này (bao gồm)
	 * @param toExclusive đơn tạo trước thời điểm này
	 */
	static Specification<Booking> adminFilter(Integer tripId, Collection<BookingStatus> statuses, String keyword,
			LocalDateTime from, LocalDateTime toExclusive) {
		return (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (tripId != null) {
				where.add(cb.equal(root.get("trip").get("id"), tripId));
			}
			if (statuses != null && !statuses.isEmpty()) {
				where.add(root.get("status").in(statuses));
			}
			if (keyword != null && !keyword.isBlank()) {
				String like = "%" + LikePattern.escape(keyword.trim().toLowerCase()) + "%";
				char esc = '\\';
				where.add(cb.or(
						cb.like(cb.lower(root.get("bookingCode")), like, esc),
						cb.like(cb.lower(root.get("contactName")), like, esc),
						cb.like(cb.lower(root.get("contactPhone")), like, esc),
						cb.like(cb.lower(root.get("contactEmail")), like, esc)));
			}
			if (from != null) {
				where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (toExclusive != null) {
				where.add(cb.lessThan(root.get("createdAt"), toExclusive));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	/**
	 * Trang "Đơn hàng của tôi".
	 *
	 * <p>{@code statuses} luôn được truyền đủ (tất cả trạng thái khi người dùng
	 * không lọc) nên chỉ cần một method thay vì hai. {@code @EntityGraph} nạp sẵn
	 * chuyến/tuyến/nhà xe/điểm đón-trả để render 20 dòng trong trang mà không
	 * sinh N+1. Index phục vụ: {@code ix_bookings_user_status}.
	 */
	@EntityGraph(attributePaths = { "trip", "trip.route", "trip.route.origin", "trip.route.destination",
			"trip.company", "pickupPoint", "dropoffPoint" })
	Page<Booking> findByUserIdAndStatusIn(Integer userId, Collection<BookingStatus> statuses, Pageable pageable);

	/** Đọc trạng thái mới nhất từ DB, bỏ qua bản trong phiên; dùng sau khi đã khoá ghế. */
	@Query("SELECT b.status FROM Booking b WHERE b.id = :id")
	Optional<BookingStatus> findStatusById(@Param("id") Integer id);

	/** Kiểm tra quyền sở hữu khi xem chi tiết đơn. */
	Optional<Booking> findByIdAndUserId(Integer id, Integer userId);

	/** Trang Tra cứu vé của khách vãng lai: mã vé + SĐT, khớp index ix_bookings_code_phone. */
	Optional<Booking> findByBookingCodeAndContactPhone(String bookingCode, String contactPhone);

	boolean existsByBookingCode(String bookingCode);

	/** Mọi đơn của một chuyến theo trạng thái, không phân trang: dùng khi huỷ chuyến. */
	List<Booking> findByTripIdAndStatusIn(Integer tripId, Collection<BookingStatus> statuses);

	boolean existsByTripId(Integer tripId);

	/** Số đơn theo trạng thái của một chuyến; mỗi dòng là [status, soLuong]. */
	@Query("SELECT b.status, count(b) FROM Booking b WHERE b.trip.id = :tripId GROUP BY b.status")
	List<Object[]> countByStatusOfTrip(@Param("tripId") Integer tripId);

	/** Đơn PAID của mọi chuyến đã COMPLETED thành COMPLETED, dùng cho job định kỳ. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Booking b
			SET b.status = :completed
			WHERE b.status = :paid
			  AND b.trip.id IN (SELECT t.id FROM Trip t WHERE t.status = :tripCompleted)
			""")
	int completePaidBookingsOfCompletedTrips(@Param("completed") BookingStatus completed,
			@Param("paid") BookingStatus paid,
			@Param("tripCompleted") TripStatus tripCompleted);

	/** Đơn PAID của một chuyến thành COMPLETED, dùng khi Admin bấm hoàn thành. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Booking b
			SET b.status = :completed
			WHERE b.status = :paid AND b.trip.id = :tripId
			""")
	int completePaidBookingsOfTrip(@Param("tripId") Integer tripId,
			@Param("completed") BookingStatus completed,
			@Param("paid") BookingStatus paid);

	/**
	 * Đánh dấu mọi đơn PENDING đã quá hạn thành EXPIRED trong một câu UPDATE.
	 *
	 * <p>Ghế tương ứng đã được {@code releaseExpiredHolds} trả về AVAILABLE. Điều
	 * kiện {@code status = PENDING} bảo đảm đơn vừa thanh toán xong không bị đánh
	 * EXPIRED nhầm (đã là PAID).
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Booking b
			SET b.status = :expired
			WHERE b.status = :pending AND b.expiresAt < :now
			""")
	int expirePendingBookings(@Param("expired") BookingStatus expired,
			@Param("pending") BookingStatus pending,
			@Param("now") LocalDateTime now);

	// ---- Thống kê Dashboard [3.5]. Khoảng thời gian luôn là [from, to). ----

	/** Mỗi dòng: [paidAt, totalPrice, soGhe] của đơn đã thanh toán; gom theo ngày ở service. */
	@Query("""
			SELECT b.paidAt, b.totalPrice, (SELECT count(d) FROM BookingDetail d WHERE d.booking = b)
			FROM Booking b
			WHERE b.status IN :statuses AND b.paidAt >= :from AND b.paidAt < :to
			  AND (:companyId IS NULL OR b.trip.company.id = :companyId)
			""")
	List<Object[]> paidRowsBetween(@Param("statuses") Collection<BookingStatus> statuses,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("companyId") Integer companyId);

	@Query("""
			SELECT count(b) FROM Booking b
			WHERE b.createdAt >= :from AND b.createdAt < :to AND (:companyId IS NULL OR b.trip.company.id = :companyId)
			""")
	long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("companyId") Integer companyId);

	@Query("""
			SELECT count(b) FROM Booking b
			WHERE b.status = :cancelled AND b.cancelledAt >= :from AND b.cancelledAt < :to
			  AND (:companyId IS NULL OR b.trip.company.id = :companyId)
			""")
	long countCancelledBetween(@Param("cancelled") BookingStatus cancelled,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("companyId") Integer companyId);

	/** Mỗi dòng: [status, soLuong] của đơn tạo trong khoảng. */
	@Query("""
			SELECT b.status, count(b) FROM Booking b
			WHERE b.createdAt >= :from AND b.createdAt < :to AND (:companyId IS NULL OR b.trip.company.id = :companyId)
			GROUP BY b.status
			""")
	List<Object[]> countByStatusCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("companyId") Integer companyId);

	/** Mỗi dòng: [routeId, tenTuyen, diemDi, diemDen, soDon, doanhThu], xếp theo doanh thu giảm dần. */
	@Query("""
			SELECT r.id, r.name, o.name, de.name, count(b), sum(b.totalPrice)
			FROM Booking b JOIN b.trip t JOIN t.route r JOIN r.origin o JOIN r.destination de
			WHERE b.status IN :statuses AND b.paidAt >= :from AND b.paidAt < :to
			  AND (:companyId IS NULL OR t.company.id = :companyId)
			GROUP BY r.id, r.name, o.name, de.name
			ORDER BY sum(b.totalPrice) DESC, count(b) DESC
			""")
	List<Object[]> topRoutesBetween(@Param("statuses") Collection<BookingStatus> statuses,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("companyId") Integer companyId, Pageable pageable);

	/** Mỗi dòng: [routeId, soGhe] cho các tuyến đã chọn. */
	@Query("""
			SELECT b.trip.route.id, count(d)
			FROM BookingDetail d JOIN d.booking b
			WHERE b.status IN :statuses AND b.paidAt >= :from AND b.paidAt < :to AND b.trip.route.id IN :routeIds
			  AND (:companyId IS NULL OR b.trip.company.id = :companyId)
			GROUP BY b.trip.route.id
			""")
	List<Object[]> ticketsByRouteBetween(@Param("statuses") Collection<BookingStatus> statuses,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("routeIds") Collection<Integer> routeIds, @Param("companyId") Integer companyId);
}
