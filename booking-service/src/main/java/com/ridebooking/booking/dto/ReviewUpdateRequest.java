package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body cho {@code PUT /api/reviews/{id}}. Không đổi được đơn hay chuyến của đánh giá. */
@Schema(description = "Sửa điểm và bình luận của đánh giá")
public record ReviewUpdateRequest(

        @NotNull(message = "rating: Vui lòng chọn số sao")
        @Min(value = 1, message = "rating: Số sao từ 1 đến 5")
        @Max(value = 5, message = "rating: Số sao từ 1 đến 5")
        @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer rating,

        @Size(max = 1000, message = "comment: Bình luận tối đa 1000 ký tự")
        @Schema(example = "Xe tốt nhưng hơi trễ 15 phút")
        String comment) {
}
