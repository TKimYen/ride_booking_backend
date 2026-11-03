package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.BookingDetail;
import com.ridebooking.booking.enums.BookingStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingDetailRepository extends JpaRepository<BookingDetail, Integer> {

	/** Số vé đã bán theo tuyến, nhiều nhất trước; mỗi dòng là [routeId, soVe]. */
	@Query("""
			SELECT d.booking.trip.route.id, count(d)
			FROM BookingDetail d
			WHERE d.booking.status IN :statuses
			GROUP BY d.booking.trip.route.id
			ORDER BY count(d) DESC, d.booking.trip.route.id ASC
			""")
	List<Object[]> countSoldByRoute(@Param("statuses") Collection<BookingStatus> statuses, Pageable pageable);

	/** Số vé đã bán của các tuyến chỉ định; mỗi dòng là [routeId, soVe]. */
	@Query("""
			SELECT d.booking.trip.route.id, count(d)
			FROM BookingDetail d
			WHERE d.booking.status IN :statuses AND d.booking.trip.route.id IN :routeIds
			GROUP BY d.booking.trip.route.id
			""")
	List<Object[]> countSoldOfRoutes(@Param("statuses") Collection<BookingStatus> statuses,
			@Param("routeIds") Collection<Integer> routeIds);

	/**
	 * Các ghế của một đơn, nạp sẵn thông tin ghế để render (tên, tầng, giá) mà
	 * không thêm truy vấn. Sắp theo tầng rồi tên ghế cho hiển thị ổn định.
	 */
	@Query("""
			SELECT d FROM BookingDetail d
			JOIN FETCH d.seat
			WHERE d.booking.id = :bookingId
			ORDER BY d.seat.floor ASC, d.seat.seatName ASC
			""")
	List<BookingDetail> findByBookingIdWithSeat(@Param("bookingId") Integer bookingId);

	/**
	 * Ghế của NHIỀU đơn trong một truy vấn, dùng cho trang danh sách.
	 *
	 * <p>Gọi {@code findByBookingIdWithSeat} trong vòng lặp 20 đơn là 20 truy vấn
	 * (N+1). Service gom kết quả theo {@code booking.id}.
	 */
	@Query("""
			SELECT d FROM BookingDetail d
			JOIN FETCH d.seat
			WHERE d.booking.id IN :bookingIds
			ORDER BY d.seat.floor ASC, d.seat.seatName ASC
			""")
	List<BookingDetail> findByBookingIdsWithSeat(@Param("bookingIds") Collection<Integer> bookingIds);
}
