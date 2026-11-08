package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body cho {@code PATCH /api/admin/reviews/{id}/hidden}. */
@Schema(description = "Ẩn hoặc hiện lại một đánh giá")
public record ReviewVisibilityRequest(
        @NotNull(message = "hidden: Thiếu trạng thái ẩn")
        @Schema(description = "true để ẩn, false để hiện lại", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean hidden) {
}
