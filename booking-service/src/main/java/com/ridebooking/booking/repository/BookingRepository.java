package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.enums.BookingStatus;
import java.time.LocalDateTime;
import java.util.Collection;
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
