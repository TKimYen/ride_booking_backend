package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Yêu cầu kiểm tra mã khuyến mãi")
public record PromotionValidateRequest(
        @NotNull(message = "Chuyến xe không được bỏ trống")
        @Schema(description = "ID của chuyến xe", example = "1")
        Integer tripId,

        @NotEmpty(message = "Danh sách ghế không được rỗng")
        @Schema(description = "Danh sách ID ghế đã chọn", example = "[10, 11]")
        List<Integer> seatIds,

        @NotBlank(message = "Mã khuyến mãi không được bỏ trống")
        @Schema(description = "Mã khuyến mãi cần kiểm tra", example = "HE2026")
        String code) {
}

