package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body cho POST/PUT {@code /api/admin/routes}. */
public record RouteRequest(

        @NotNull(message = "Điểm xuất phát không được bỏ trống")
        @Schema(description = "id của locations, nơi xuất phát", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer originId,

        @NotNull(message = "Điểm đến không được bỏ trống")
        @Schema(description = "id của locations, nơi đến", example = "8", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer destinationId,

        @NotBlank(message = "Tên tuyến không được để trống")
        @Size(max = 200, message = "Tên tuyến không được vượt quá 200 ký tự")
        @Schema(description = "Tên tuyến", example = "Hà Nội - Sa Pa", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Min(value = 1, message = "Quãng đường phải lớn hơn 0")
        @Schema(description = "Quãng đường tính bằng km", example = "155.5")
        BigDecimal distanceKm,

        @Min(value = 1, message = "Thời gian di chuyển phải lớn hơn 0")
        @Schema(description = "Thời gian di chuyển tính bằng phút", example = "300")
        Integer durationMinutes) {
}
