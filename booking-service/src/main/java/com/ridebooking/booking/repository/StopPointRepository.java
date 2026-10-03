package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.StopPoint;
import com.ridebooking.booking.enums.PointType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StopPointRepository extends JpaRepository<StopPoint, Integer> {

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
