package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body thêm/sửa mốc hoàn tiền của nhà xe. */
public record CancellationRuleRequest(

        @NotNull(message = "Số giờ trước khởi hành không được bỏ trống")
        @Min(value = 0, message = "Số giờ trước khởi hành không được âm")
        @Schema(example = "24", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer hoursBefore,

        @NotNull(message = "Phần trăm hoàn không được bỏ trống")
        @Min(value = 0, message = "Phần trăm hoàn từ 0 đến 100")
        @Max(value = 100, message = "Phần trăm hoàn từ 0 đến 100")
        @Schema(example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer refundPercent) {
}
