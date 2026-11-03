package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.TripStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Integer> {

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

	/** Trang Quản lý Đơn hàng của Admin, không lọc theo chuyến. */
	@EntityGraph(attributePaths = { "trip", "trip.route", "trip.route.origin", "trip.route.destination",
			"trip.company", "pickupPoint", "dropoffPoint" })
	Page<Booking> findByStatusIn(Collection<BookingStatus> statuses, Pageable pageable);

	/** Trang Quản lý Đơn hàng lọc theo chuyến. Index: {@code ix_bookings_trip}. */
	@EntityGraph(attributePaths = { "trip", "trip.route", "trip.route.origin", "trip.route.destination",
			"trip.company", "pickupPoint", "dropoffPoint" })
	Page<Booking> findByTripIdAndStatusIn(Integer tripId, Collection<BookingStatus> statuses, Pageable pageable);

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
}
