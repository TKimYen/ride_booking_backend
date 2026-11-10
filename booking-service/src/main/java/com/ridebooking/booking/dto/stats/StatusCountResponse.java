package com.ridebooking.booking.dto.stats;

import io.swagger.v3.oas.annotations.media.Schema;

/** Số đơn theo trạng thái (biểu đồ tròn). Đủ mọi trạng thái, kể cả số 0. */
@Schema(description = "Số đơn của một trạng thái")
public record StatusCountResponse(
        @Schema(example = "PAID") String status,
        @Schema(example = "30") long count) {
}
