package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Trip;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một chuyến xe. Dùng cho cả danh sách kết quả lẫn Trang chi tiết. */
@Schema(description = "Một chuyến xe")
public record TripResponse(

        @Schema(example = "17") Integer id,
        @Schema(example = "4") Integer routeId,
        @Schema(example = "Hà Nội - Sa Pa") String routeName,
        @Schema(example = "1") Integer originId,
        @Schema(example = "Hà Nội") String originName,
        @Schema(example = "8") Integer destinationId,
        @Schema(example = "Sa Pa") String destinationName,

        @Schema(example = "3") Integer companyId,
        @Schema(example = "G8 SAPA OPEN TOUR") String companyName,
        @Schema(example = "https://cdn.../logo.png") String companyLogoUrl,

        @Schema(example = "LIMOUSINE_29") String busType,
        @Schema(description = "Tên loại xe để hiển thị", example = "Limousine 29 chỗ") String busTypeName,
        @Schema(description = "Tổng số ghế của chuyến", example = "29") Integer totalSeats,

        @Schema(example = "2026-10-05T06:00:00") LocalDateTime departureTime,
        @Schema(example = "2026-10-05T11:00:00") LocalDateTime arrivalTime,
        @Schema(example = "250000.00") BigDecimal basePrice,
        @Schema(example = "ACTIVE") String status) {

    public static TripResponse from(Trip trip) {
        return from(trip, null);
    }

    /**
     * @param totalSeats tổng số ghế thực tế đã sinh, {@code null} thì lấy theo
     *                   {@code BusType.seatCount()}
     */
    public static TripResponse from(Trip trip, Integer totalSeats) {
        return new TripResponse(
                trip.getId(),
                trip.getRoute().getId(),
                trip.getRoute().getName(),
                trip.getRoute().getOrigin().getId(),
                trip.getRoute().getOrigin().getName(),
                trip.getRoute().getDestination().getId(),
                trip.getRoute().getDestination().getName(),
                trip.getCompany().getId(),
                trip.getCompany().getName(),
                trip.getCompany().getLogoUrl(),
                trip.getBusType().name(),
                trip.getBusType().displayName(),
                totalSeats != null ? totalSeats : trip.getBusType().seatCount(),
                trip.getDepartureTime(),
                trip.getArrivalTime(),
                trip.getBasePrice(),
                trip.getStatus().name());
    }
}
