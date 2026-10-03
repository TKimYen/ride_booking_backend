package com.ridebooking.booking.service;

import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job định kỳ trả ghế quá hạn và đánh dấu đơn EXPIRED.
 *
 * <p>Đây là LƯỚI AN TOÀN, không phải cơ chế chính: luồng giữ ghế đã tự coi ghế
 * HOLDING quá hạn là trống và giành lại ngay. Job chỉ dọn dẹp để dữ liệu hiển thị
 * đúng (ghế trở về AVAILABLE, đơn PENDING thành EXPIRED) mà không phải chờ người
 * dùng thao tác.
 *
 * <p>Hai câu UPDATE tập hợp, không quét từng dòng. Điều kiện {@code < now} bảo
 * đảm không đụng vào ghế vừa được giữ lại hoặc đơn vừa thanh toán: DB đánh giá
 * lại điều kiện sau khi lấy khoá dòng, và đơn PAID không còn khớp
 * {@code status = PENDING}.
 *
 * <p>Tắt bằng {@code app.booking.expiry-job.enabled=false} (test đặt false để
 * job không chen vào giữa các ca kiểm thử).
 */
@Component
@ConditionalOnProperty(name = "app.booking.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final TripSeatRepository tripSeatRepository;
    private final BookingRepository bookingRepository;

    public BookingExpiryJob(TripSeatRepository tripSeatRepository, BookingRepository bookingRepository) {
        this.tripSeatRepository = tripSeatRepository;
        this.bookingRepository = bookingRepository;
    }

    @Scheduled(
            fixedDelayString = "${app.booking.expiry-job.interval-ms:60000}",
            initialDelayString = "${app.booking.expiry-job.initial-delay-ms:60000}")
    @Transactional
    public void releaseExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        int releasedSeats = tripSeatRepository.releaseExpiredHolds(
                SeatStatus.AVAILABLE, SeatStatus.HOLDING, now);
        int expiredBookings = bookingRepository.expirePendingBookings(
                BookingStatus.EXPIRED, BookingStatus.PENDING, now);
        if (releasedSeats > 0 || expiredBookings > 0) {
            log.info("Job hết hạn: trả {} ghế, đánh dấu {} đơn EXPIRED", releasedSeats, expiredBookings);
        }
    }
}
