package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.StopPoint;
import com.ridebooking.booking.enums.PointType;
import com.ridebooking.booking.enums.TripStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StopPointRepository extends JpaRepository<StopPoint, Integer> {

	/** Điểm dừng của cả trang kết quả trong một câu, để lấy điểm đón đầu / điểm trả cuối. */
	@Query("SELECT sp FROM StopPoint sp WHERE sp.trip.id IN :tripIds ORDER BY sp.trip.id, sp.stopOrder")
	List<StopPoint> findByTripIds(@Param("tripIds") Collection<Integer> tripIds);

	/**
	 * Cặp [loại điểm, tên] khác nhau của các chuyến đang bán trong khoảng giờ khởi hành.
	 * {@code originId} / {@code destinationId} = 0 nghĩa là không lọc — tránh tham số
	 * NULL trong JPQL, vốn bị PostgreSQL từ chối khi không suy ra được kiểu.
	 */
	@Query("""
			SELECT DISTINCT sp.pointType, sp.name FROM StopPoint sp
			JOIN sp.trip t
			WHERE t.status = :status
			  AND t.departureTime >= :from AND t.departureTime <= :to
			  AND (:originId = 0 OR t.route.origin.id = :originId)
			  AND (:destinationId = 0 OR t.route.destination.id = :destinationId)
			""")
	List<Object[]> findDistinctNames(@Param("status") TripStatus status,
									 @Param("from") LocalDateTime from,
									 @Param("to") LocalDateTime to,
									 @Param("originId") int originId,
									 @Param("destinationId") int destinationId);

	/** Điểm đón và điểm trả của một chuyến, sẵn sàng hiển thị. */
	List<StopPoint> findByTripIdOrderByStopOrderAsc(Integer tripId);

	/** Các điểm cùng loại của một chuyến, dùng để cấp lại số thứ tự khi xoá. */
	List<StopPoint> findByTripIdAndPointTypeOrderByStopOrderAsc(Integer tripId, PointType pointType);

	/**
	 * Số thứ tự lớn nhất đang dùng, để thêm mới nối tiếp.
	 *
	 * <p>{@code COALESCE} về 0 khi chưa có điểm nào: điểm đầu tiên của một chuyến
	 * phải là lần 1, không phải lần 0.
	 */
	@Query("""
			SELECT COALESCE(MAX(sp.stopOrder), 0) FROM StopPoint sp
			WHERE sp.trip.id = :tripId AND sp.pointType = :pointType
			""")
	Integer findMaxStopOrder(@Param("tripId") Integer tripId, @Param("pointType") PointType pointType);
}
