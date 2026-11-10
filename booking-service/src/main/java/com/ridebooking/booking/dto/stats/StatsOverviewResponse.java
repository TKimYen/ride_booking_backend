package com.ridebooking.booking.dto.stats;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Các ô số liệu đầu trang Dashboard [3.5]. */
@Schema(description = "Tổng quan trong khoảng ngày")
public record StatsOverviewResponse(
        @Schema(example = "2026-10-11") LocalDate from,
        @Schema(example = "2026-11-09") LocalDate to,
        @Schema(description = "Tổng tiền đơn PAID + COMPLETED, tính theo ngày thanh toán", example = "15400000.00")
        BigDecimal revenue,
        @Schema(description = "Số đơn đã thanh toán (PAID + COMPLETED) theo ngày thanh toán", example = "42")
        long paidBookings,
        @Schema(description = "Số vé (ghế) của các đơn đã thanh toán", example = "77")
        long ticketsSold,
        @Schema(description = "Số đơn mới tạo theo ngày tạo, mọi trạng thái", example = "60")
        long newBookings,
        @Schema(description = "Số đơn bị huỷ theo ngày huỷ", example = "5")
        long cancelledBookings,
        @Schema(description = "Số chuyến ACTIVE chưa khởi hành, tại thời điểm gọi", example = "120")
        long upcomingTrips) {
}
