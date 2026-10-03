package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Tóm tắt điểm đánh giá của một chuyến.
 *
 * <p>{@code ratingBreakdown} luôn có đủ 5 mức, kể cả mức 0 lượt, để frontend vẽ
 * đủ 5 thanh. Thiếu mức thì frontend dễ vẽ 3 thanh rồi tính sai tỉ lệ.
 */
@Schema(description = "Điểm trung bình và phân bố sao của một chuyến")
public record ReviewSummaryResponse(

        @Schema(description = "Điểm trung bình, làm tròn 1 chữ số. Chưa có đánh giá thì null.",
                example = "4.5") BigDecimal averageRating,
        @Schema(example = "12") Long totalReviews,
        @Schema(description = "Số lượt ở mỗi mức, khoá là 1..5")
        Map<Integer, Long> ratingBreakdown) {

    public static ReviewSummaryResponse from(BigDecimal average, long total, Map<Integer, Long> breakdown) {
        BigDecimal rounded = total == 0 ? null : average.setScale(1, RoundingMode.HALF_UP);
        return new ReviewSummaryResponse(rounded, total, breakdown);
    }

    /** Bổ sung các mức chưa có lượt đánh giá với giá trị 0. */
    public static Map<Integer, Long> withAllLevels(Map<Integer, Long> breakdown) {
        return java.util.stream.IntStream.rangeClosed(1, 5)
                .boxed()
                .collect(java.util.stream.Collectors.toMap(
                        level -> level,
                        level -> breakdown.getOrDefault(level, 0L)));
    }
}
