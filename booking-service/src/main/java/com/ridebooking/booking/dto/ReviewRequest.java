package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body cho {@code POST /api/reviews}.
 *
 * <p>Nhận {@code bookingId} chứ không nhận {@code tripId}: đơn chứng minh người
 * viết đã đi chuyến đó. Nhận {@code tripId} thì ai cũng đánh giá được mọi chuyến.
 */
@Schema(description = "Viết đánh giá cho một đơn đã đi")
public record ReviewRequest(

        @NotNull(message = "bookingId: Thiếu mã đơn")
        @Schema(description = "id đơn đã đi (COMPLETED) của chính người viết", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer bookingId,

        @NotNull(message = "rating: Vui lòng chọn số sao")
        @Min(value = 1, message = "rating: Số sao từ 1 đến 5")
        @Max(value = 5, message = "rating: Số sao từ 1 đến 5")
        @Schema(description = "Điểm 1..5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer rating,

        @Size(max = 1000, message = "comment: Bình luận tối đa 1000 ký tự")
        @Schema(description = "Bình luận, không bắt buộc", example = "Xe sạch, tài xế đúng giờ")
        String comment) {
}
