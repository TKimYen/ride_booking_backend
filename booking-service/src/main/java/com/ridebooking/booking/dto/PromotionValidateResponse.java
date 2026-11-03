package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Kết quả kiểm tra mã khuyến mãi")
public record PromotionValidateResponse(
        @Schema(description = "Mã khuyến mãi", example = "HE2026")
        String code,

        @Schema(description = "Tổng tiền trước khi giảm", example = "500000.00")
        BigDecimal subtotal,

        @Schema(description = "Số tiền được giảm", example = "50000.00")
        BigDecimal discount,

        @Schema(description = "Số tiền còn lại sau khi giảm", example = "450000.00")
        BigDecimal total) {
}

