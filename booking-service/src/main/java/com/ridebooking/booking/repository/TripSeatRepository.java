package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripSeatRepository extends JpaRepository<TripSeat, Integer> {

	List<TripSeat> findByTripIdOrderByFloorAscSeatNameAsc(Integer tripId);

	long countByTripIdAndStatus(Integer tripId, SeatStatus status);

	boolean existsByTripIdAndSeatName(Integer tripId, String seatName);

	/**
	 * Đếm ghế theo trạng thái cho NHIỀU chuyến trong MỘT câu truy vấn.
	 *
	 * <p>Trang Danh sách kết quả trả về 20 chuyến. Gọi
	 * {@code countByTripIdAndStatus} trong vòng lặp sẽ tạo 40 câu truy vấn
	 * (N+1) — nhanh thì chạy được lúc dev, nhưng lên 100 chuyến là 200 query cho
	 * một lần tải trang. Gom vào một query {@code GROUP BY} giữ nguyên kết quả với
	 * 2 câu truy vấn.
	 *
	 * <p>Index phục vụ: {@code ix_trip_seats_trip_status (trip_id, status)}.
	 *
	 * @return mỗi dòng là [tripId, status, soLuong]
	 */
	@Query("""
			SELECT s.trip.id, s.status, count(s)
			FROM TripSeat s
			WHERE s.trip.id IN :tripIds
			GROUP BY s.trip.id, s.status
			""")
	List<Object[]> countByTripIdsGroupedByStatus(@Param("tripIds") List<Integer> tripIds);

	/**
	 * Tổng số ghế đã sinh của mỗi chuyến trong danh sách.
	 *
	 * <p>Tách khỏi query trên vì lần này cần TỔNG, không cần tách theo trạng
	 * thái. Gộp chung thì phải cộng tay trong Java, dễ sót.
	 */
	@Query("""
			SELECT s.trip.id, count(s)
			FROM TripSeat s
			WHERE s.trip.id IN :tripIds
			GROUP BY s.trip.id
			""")
	List<Object[]> countTotalByTripIds(@Param("tripIds") List<Integer> tripIds);

	/**
	 * Khoá ghi các ghế theo id trước khi kiểm tra và đổi trạng thái.
	 *
	 * <p>Đây là chốt chặn chống bán trùng ghế. Điều kiện "một ghế chỉ thuộc một
	 * đơn đang hiệu lực" trải qua hai bảng (bookings + booking_details) nên không
	 * biểu diễn được bằng UNIQUE — nó phải dựa vào khoá dòng này. Mọi luồng giữ
	 * ghế / thanh toán / huỷ / hết hạn đều phải đi qua đây trước khi ghi.
	 *
	 * <p>Danh sách id được sắp xếp tăng dần ở tầng service để hai giao dịch giành
	 * các ghế khác thứ tự vẫn khoá theo cùng một thứ tự, tránh deadlock.
	 *
	 * <p>Dùng {@code SELECT ... FOR UPDATE}. Trên PostgreSQL, câu lệnh trả về
	 * theo thứ tự khoá; service đã sort id nên thứ tự ổn định.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM TripSeat s WHERE s.id IN :ids ORDER BY s.id")
	List<TripSeat> findAllByIdForUpdate(@Param("ids") List<Integer> ids);

	/**
	 * Trả mọi ghế đang HOLDING đã quá hạn về AVAILABLE trong MỘT câu UPDATE.
	 *
	 * <p>Job chạy mỗi phút; quét từng dòng trong Java là N+1 và mở nhiều
	 * transaction. Điều kiện {@code holdingUntil < now} bảo đảm không đụng vào ghế
	 * vừa được giữ lại (holdingUntil đã ở tương lai).
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE TripSeat s
			SET s.status = :available, s.holdingUntil = NULL, s.updatedAt = :now
			WHERE s.status = :holding AND s.holdingUntil < :now
			""")
	int releaseExpiredHolds(@Param("available") SeatStatus available,
			@Param("holding") SeatStatus holding,
			@Param("now") LocalDateTime now);
}