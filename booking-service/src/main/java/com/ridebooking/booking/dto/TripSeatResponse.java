package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.TripSeat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một ghế trong sơ đồ. Frontend dùng để vẽ và đổi màu theo {@code status}. */
@Schema(description = "Một ghế trong sơ đồ ghế")
public record TripSeatResponse(

        @Schema(example = "120") Integer id,
        @Schema(description = "id chuyến", example = "17") Integer tripId,
        @Schema(example = "A1") String seatName,
        @Schema(description = "1 = tầng dưới, 2 = tầng trên", example = "1") Integer floor,
        @Schema(description = "STANDARD | DOUBLE | SINGLE", example = "DOUBLE") String seatType,
        @Schema(description = "Giá chênh lệch cộng thêm vào giá chuyến", example = "250000.00") BigDecimal extraPrice,
        @Schema(description = "Giá vé thực tế của ghế này", example = "500000.00") BigDecimal totalPrice,
        @Schema(description = "AVAILABLE | HOLDING | BOOKED", example = "AVAILABLE") String status,
        @Schema(description = "Hạn giữ chỗ, chỉ có khi status = HOLDING")
        LocalDateTime holdingUntil) {

    public static TripSeatResponse from(TripSeat seat, BigDecimal tripBasePrice) {
        return new TripSeatResponse(
                seat.getId(),
                seat.getTrip().getId(),
                seat.getSeatName(),
                seat.getFloor(),
                seat.getSeatType(),
                seat.getExtraPrice(),
                seat.effectivePrice(tripBasePrice),
                seat.getStatus().name(),
                seat.getHoldingUntil());
    }
}
