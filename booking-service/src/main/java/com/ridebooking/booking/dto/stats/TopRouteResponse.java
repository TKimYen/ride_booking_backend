package com.ridebooking.booking.dto.stats;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Tuyến bán chạy nhất trong khoảng ngày, xếp theo doanh thu. */
@Schema(description = "Tuyến bán chạy")
public record TopRouteResponse(
        @Schema(example = "3") Integer routeId,
        @Schema(example = "Sài Gòn - Đà Lạt") String routeName,
        @Schema(example = "TP. Hồ Chí Minh") String originName,
        @Schema(example = "Đà Lạt") String destinationName,
        @Schema(example = "18") long bookings,
        @Schema(example = "31") long tickets,
        @Schema(example = "8400000.00") BigDecimal revenue) {
}
