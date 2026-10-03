package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.BookingDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Một ghế trong một đơn, kèm giá đã chốt lúc mua. */
@Schema(description = "Một ghế của đơn đặt vé")
public record BookingSeatResponse(

        @Schema(example = "120") Integer seatId,
        @Schema(example = "A1") String seatName,
        @Schema(description = "1 = tầng dưới, 2 = tầng trên", example = "1") Integer floor,
        @Schema(description = "STANDARD | DOUBLE | SINGLE", example = "DOUBLE") String seatType,
        @Schema(description = "Giá chốt lúc mua, không đổi khi nhà xe tăng giá", example = "500000.00")
        BigDecimal price) {

    public static BookingSeatResponse from(BookingDetail detail) {
        return new BookingSeatResponse(
                detail.getSeat().getId(),
                detail.getSeat().getSeatName(),
                detail.getSeat().getFloor(),
                detail.getSeat().getSeatType(),
                detail.getPrice());
    }
}
