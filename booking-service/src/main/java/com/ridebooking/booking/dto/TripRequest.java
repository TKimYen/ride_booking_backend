package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Body cho POST/PUT {@code /api/admin/trips}. */
public record TripRequest(

        @NotNull(message = "Tuyến không được bỏ trống")
        @Schema(description = "id của routes", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer routeId,

        @NotNull(message = "Nhà xe không được bỏ trống")
        @Schema(description = "id của bus_companies", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer companyId,

        @NotBlank(message = "Loại xe không được bỏ trống")
        @Schema(description = "LIMOUSINE_29 | CABIN_DOUBLE_22 | SLEEPER_45. Quyết định số ghế và sơ đồ ghế.",
                example = "LIMOUSINE_29", requiredMode = Schema.RequiredMode.REQUIRED)
        String busType,

        @NotNull(message = "Giờ khởi hành không được bỏ trống")
        @Schema(example = "2026-10-05T06:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime departureTime,

        @NotNull(message = "Giờ đến không được bỏ trống")
        @Schema(example = "2026-10-05T11:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime arrivalTime,

        @NotNull(message = "Giá vé không được bỏ trống")
        @DecimalMin(value = "0", message = "Giá vé không được nhỏ hơn 0")
        @Schema(description = "Giá vé cơ bản, chưa cộng phần chênh lệch của ghế", example = "250000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal basePrice,

        @Schema(description = "Trạng thái. Bỏ trống thì ACTIVE.", example = "ACTIVE",
                allowableValues = {"ACTIVE", "CANCELLED", "COMPLETED"})
        String status) {
}
