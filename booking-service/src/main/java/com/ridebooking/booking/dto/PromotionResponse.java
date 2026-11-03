package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Promotion;
import com.ridebooking.booking.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Thông tin mã khuyến mãi")
public record PromotionResponse(
        @Schema(example = "1") Integer id,
        @Schema(example = "HE2026") String code,
        @Schema(example = "Chào hè 2026") String title,
        @Schema(example = "Giảm 10% tối đa 50k cho mọi chuyến") String description,
        @Schema(example = "https://example.com/banner.jpg") String bannerUrl,
        @Schema(example = "PERCENT") DiscountType discountType,
        @Schema(example = "10.00") BigDecimal discountValue,
        @Schema(example = "50000.00") BigDecimal maxDiscount,
        @Schema(example = "200000.00") BigDecimal minOrderValue,
        @Schema(example = "1") Integer companyId,
        @Schema(example = "Nhà xe Sao Việt") String companyName,
        @Schema(example = "2") Integer routeId,
        @Schema(example = "Hà Nội - Sa Pa") String routeName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer usageLimit,
        Integer usedCount,
        boolean featured,
        boolean active) {

    public static PromotionResponse from(Promotion p) {
        return new PromotionResponse(
                p.getId(),
                p.getCode(),
                p.getTitle(),
                p.getDescription(),
                p.getBannerUrl(),
                p.getDiscountType(),
                p.getDiscountValue(),
                p.getMaxDiscount(),
                p.getMinOrderValue(),
                p.getCompany() != null ? p.getCompany().getId() : null,
                p.getCompany() != null ? p.getCompany().getName() : null,
                p.getRoute() != null ? p.getRoute().getId() : null,
                p.getRoute() != null ? p.getRoute().getName() : null,
                p.getStartsAt(),
                p.getEndsAt(),
                p.getUsageLimit(),
                p.getUsedCount() != null ? p.getUsedCount() : 0,
                p.isFeatured(),
                p.isActive());
    }
}

