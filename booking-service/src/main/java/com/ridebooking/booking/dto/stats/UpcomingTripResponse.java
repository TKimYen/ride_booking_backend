package com.ridebooking.booking.dto.stats;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Chuyến xe sắp chạy")
public record UpcomingTripResponse(
        @Schema(example = "42") Integer tripId,
        @Schema(example = "Hà Nội - Sa Pa") String routeName,
        @Schema(example = "XeGo Limousine") String companyName,
        LocalDateTime departureTime,
        @Schema(description = "Ghế đã bán hoặc đang giữ") long booked,
        @Schema(description = "Tổng ghế") long total) {
}
