package com.ridebooking.booking.dto.stats;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Một điểm của biểu đồ doanh thu theo ngày; ngày không có đơn vẫn có điểm với giá trị 0. */
@Schema(description = "Doanh thu một ngày")
public record DailyRevenueResponse(
        @Schema(example = "2026-11-08") LocalDate date,
        @Schema(example = "1200000.00") BigDecimal revenue,
        @Schema(example = "4") long bookings,
        @Schema(example = "7") long tickets) {
}
