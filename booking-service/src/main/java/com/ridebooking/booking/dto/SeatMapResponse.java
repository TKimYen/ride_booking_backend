package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Sơ đồ ghế đầy đủ của một chuyến, sẵn sàng cho Trang Chọn chỗ (Thành viên 3). */
@Schema(description = "Sơ đồ ghế của một chuyến")
public record SeatMapResponse(

        @Schema(example = "17") Integer tripId,
        @Schema(description = "Tên loại xe", example = "Limousine 29 chỗ") String busTypeName,
        @Schema(description = "Số tầng của xe", example = "2") Integer floors,
        @Schema(description = "Tổng số ghế", example = "29") Integer totalSeats,
        @Schema(description = "Số ghế còn trống", example = "21") Integer availableSeats,
        @Schema(description = "Giá vé cơ bản của chuyến", example = "250000.00") BigDecimal basePrice,
        @Schema(description = "Còn bao lâu thì hết giữ chỗ, null nếu không ghế nào đang giữ")
        Long holdSecondsRemaining,
        @Schema(description = "Ghế theo thứ tự tầng, rồi hàng, rồi cột")
        List<TripSeatResponse> seats) {
}
