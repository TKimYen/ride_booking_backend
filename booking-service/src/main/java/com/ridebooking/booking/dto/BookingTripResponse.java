package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Trip;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin chuyến gắn kèm một đơn, đủ để hiển thị thẻ đơn trong "Đơn hàng của tôi"
 * mà không cần gọi thêm API chi tiết chuyến.
 */
@Schema(description = "Tóm tắt chuyến của một đơn đặt vé")
public record BookingTripResponse(

        @Schema(example = "17") Integer id,
        @Schema(example = "Hà Nội - Sa Pa") String routeName,
        @Schema(example = "Hà Nội") String originName,
        @Schema(example = "Sa Pa") String destinationName,
        @Schema(example = "2026-10-05T06:00:00") LocalDateTime departureTime,
        @Schema(example = "2026-10-05T11:00:00") LocalDateTime arrivalTime,
        @Schema(example = "3") Integer companyId,
        @Schema(example = "Nhà xe Phương Bắc") String companyName,
        @Schema(description = "LIMOUSINE_29 | CABIN_DOUBLE_22 | SLEEPER_45", example = "LIMOUSINE_29")
        String busType,
        @Schema(description = "Giá vé cơ bản của chuyến", example = "250000.00") BigDecimal basePrice) {

    public static BookingTripResponse from(Trip trip) {
        return new BookingTripResponse(
                trip.getId(),
                trip.getRoute().getName(),
                trip.getRoute().getOrigin().getName(),
                trip.getRoute().getDestination().getName(),
                trip.getDepartureTime(),
                trip.getArrivalTime(),
                trip.getCompany().getId(),
                trip.getCompany().getName(),
                trip.getBusType().name(),
                trip.getBasePrice());
    }
}
