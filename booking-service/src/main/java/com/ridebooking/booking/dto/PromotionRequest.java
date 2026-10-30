package com.ridebooking.booking.dto;

import com.ridebooking.booking.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Thông tin tạo hoặc cập nhật mã khuyến mãi")
public record PromotionRequest(
        @NotBlank(message = "Mã khuyến mãi không được bỏ trống")
        @Size(max = 30, message = "Mã khuyến mãi tối đa 30 ký tự")
        @Schema(example = "HE2026")
        String code,

        @NotBlank(message = "Tiêu đề không được bỏ trống")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        @Schema(example = "Chào hè 2026")
        String title,

        @Schema(example = "Giảm 10% tối đa 50k")
        String description,

        @Schema(example = "https://example.com/banner.jpg")
        String bannerUrl,

        @NotNull(message = "Loại giảm giá không được bỏ trống")
        @Schema(example = "PERCENT")
        DiscountType discountType,

        @NotNull(message = "Giá trị giảm không được bỏ trống")
        @Schema(example = "10.00")
        BigDecimal discountValue,

        @Schema(example = "50000.00")
        BigDecimal maxDiscount,

        @Schema(example = "200000.00")
        BigDecimal minOrderValue,

        @Schema(example = "1")
        Integer companyId,

        @Schema(example = "2")
        Integer routeId,

        @NotNull(message = "Thời gian bắt đầu không được bỏ trống")
        @Schema(example = "2026-06-01T00:00:00")
        LocalDateTime startsAt,

        @NotNull(message = "Thời gian kết thúc không được bỏ trống")
        @Schema(example = "2026-08-31T23:59:59")
        LocalDateTime endsAt,

        @Schema(example = "100")
        Integer usageLimit,

        @Schema(example = "true")
        Boolean featured,

        @Schema(example = "true")
        Boolean active) {
}

