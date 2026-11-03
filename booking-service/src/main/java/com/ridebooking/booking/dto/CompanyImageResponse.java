package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.CompanyImage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một ảnh của nhà xe")
public record CompanyImageResponse(
        @Schema(example = "1") Integer id,
        @Schema(example = "https://picsum.photos/seed/xego/800/500") String imageUrl,
        @Schema(example = "Khoang giường nằm") String caption,
        @Schema(example = "1") Integer sortOrder) {

    public static CompanyImageResponse from(CompanyImage image) {
        return new CompanyImageResponse(image.getId(), image.getImageUrl(), image.getCaption(), image.getSortOrder());
    }
}
