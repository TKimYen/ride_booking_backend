package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.CancellationRule;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Huỷ trước giờ khởi hành ít nhất hoursBefore giờ thì hoàn refundPercent phần trăm")
public record CancellationRuleResponse(
        @Schema(example = "1") Integer id,
        @Schema(example = "24") Integer hoursBefore,
        @Schema(example = "50") Integer refundPercent) {

    public static CancellationRuleResponse from(CancellationRule rule) {
        return new CancellationRuleResponse(rule.getId(), rule.getHoursBefore(), rule.getRefundPercent());
    }
}
