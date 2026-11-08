package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Điểm trung bình của một nhà xe, gắn vào kết quả tìm kiếm và hồ sơ nhà xe. */
@Schema(description = "Điểm trung bình của nhà xe")
public record CompanyRatingResponse(
        @Schema(description = "Làm tròn 1 chữ số; null khi chưa có đánh giá", example = "4.6")
        BigDecimal averageRating,
        @Schema(example = "37") long totalReviews) {

    public static final CompanyRatingResponse NONE = new CompanyRatingResponse(null, 0);

    public static CompanyRatingResponse of(Number average, long total) {
        if (total == 0 || average == null) {
            return NONE;
        }
        return new CompanyRatingResponse(
                BigDecimal.valueOf(average.doubleValue()).setScale(1, RoundingMode.HALF_UP), total);
    }
}
