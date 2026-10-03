package com.ridebooking.booking.service;

import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.SeatStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Sinh danh sách ghế cho một chuyến, theo sơ đồ của loại xe.
 *
 * <p>Ghế KHÔNG được nhập tay: nhập tay thì sơ đồ lệch với tên loại xe, và một
 * chuyến 29 chỗ có thể chỉ còn 27 dòng ghế mà không ai phát hiện. Sinh từ
 * {@link BusType#layout()} nên số ghế luôn khớp loại xe.
 *
 * <p>Thành phần này CHỈ tạo object, chưa ghi database — việc lưu thuộc
 * {@link TripSeatService}, để giữ cho một chuyến nằm trong đúng một transaction.
 */
@Component
public class SeatGenerator {

    /**
     * Sinh ghế cho một chuyến, tất cả đều ở trạng thái AVAILABLE.
     *
     * <p>Mọi ghế mới đều AVAILABLE và {@code holdingUntil = null}, đúng ràng buộc
     * {@code ck_trip_seats_holding} ở DB.
     */
    public List<TripSeat> generate(Trip trip) {
        BusType busType = trip.getBusType();
        List<TripSeat> seats = new ArrayList<>(busType.seatCount());

        for (BusType.SeatSpec spec : busType.layout()) {
            TripSeat seat = new TripSeat();
            seat.setTrip(trip);
            seat.setSeatName(spec.name());
            seat.setFloor(spec.floor());
            seat.setSeatType(spec.seatType());
            seat.setExtraPrice(spec.extraPrice());
            seat.setStatus(SeatStatus.AVAILABLE);
            seats.add(seat);
        }
        return seats;
    }
}