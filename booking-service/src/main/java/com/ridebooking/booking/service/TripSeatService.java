package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.SeatMapResponse;
import com.ridebooking.booking.dto.TripSeatResponse;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sơ đồ ghế và thời gian giữ chỗ.
 *
 * <p>Giai đoạn 1 chỉ đọc trạng thái ghế. Việc giữ ghế, khoá dòng và job trả ghế
 * thuộc Giai đoạn 2 — xem db.md mục C.
 */
@Service
@Transactional(readOnly = true)
public class TripSeatService {

    private final TripSeatRepository tripSeatRepository;
    private final TripRepository tripRepository;

    /**
     * Thời gian giữ ghế, dùng cho CẢ HAI chỗ: đồng hồ đếm ngược trên header Trang
     * Chọn chỗ và job trả ghế. Cùng một biến, nếu không sẽ thành đồng hồ báo hết
     * giờ nhưng ghế vẫn bị giữ.
     */
    private final long holdMinutes;

    public TripSeatService(TripSeatRepository tripSeatRepository,
                           TripRepository tripRepository,
                           @Value("${app.booking.seat-hold-minutes:10}") long holdMinutes) {
        this.tripSeatRepository = tripSeatRepository;
        this.tripRepository = tripRepository;
        this.holdMinutes = holdMinutes;
    }

    /** Sơ đồ ghế đầy đủ của một chuyến, cho Trang Chọn chỗ & Thanh toán. */
    public SeatMapResponse seatMap(Integer tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + tripId));

        List<TripSeat> seats = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(tripId);
        long available = seats.stream().filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE).count();

        // Ghế đang giữ: đồng hồ đếm ngược theo ghế sắp hết nhất, vì khách sẽ
        // phải chờ ghế đó trước nếu muốn đặt.
        LocalDateTime soonestExpiry = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.HOLDING)
                .map(TripSeat::getHoldingUntil)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        return new SeatMapResponse(
                trip.getId(),
                trip.getBusType().displayName(),
                trip.getBusType().floors(),
                seats.size(),
                (int) available,
                trip.getBasePrice(),
                secondsRemaining(soonestExpiry),
                seats.stream().map(seat -> TripSeatResponse.from(seat, trip.getBasePrice())).toList());
    }

    /** Ghế của một chuyến, không kèm thống kê. Dùng cho màn hình admin. */
    public List<TripSeatResponse> listSeats(Integer tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + tripId));
        return tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(tripId).stream()
                .map(seat -> TripSeatResponse.from(seat, trip.getBasePrice()))
                .toList();
    }

    private Long secondsRemaining(LocalDateTime expiry) {
        if (expiry == null) {
            return null;
        }
        long seconds = Duration.between(LocalDateTime.now(), expiry).getSeconds();
        // Không trả số âm: client hiển thị "-12 giây" trông như lỗi, dù ý nghĩa là
        // "đã hết hạn, job sẽ trả ghế".
        return Math.max(seconds, 0L);
    }

    public long holdMinutes() {
        return holdMinutes;
    }
}