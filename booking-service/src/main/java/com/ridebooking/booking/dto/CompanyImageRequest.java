package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body thêm/sửa ảnh của nhà xe. */
public record CompanyImageRequest(

        @NotBlank(message = "URL ảnh không được bỏ trống")
        @Size(max = 500, message = "URL ảnh tối đa 500 ký tự")
        // Chỉ http(s): URL này được đưa thẳng vào trang của khách.
        @Pattern(regexp = "(?i)^https?://\\S+$", message = "URL ảnh phải bắt đầu bằng http:// hoặc https://")
        @Schema(example = "https://picsum.photos/seed/xego/800/500", requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,

        @Size(max = 200, message = "Chú thích tối đa 200 ký tự")
        @Schema(example = "Khoang giường nằm")
        String caption,

        @Min(value = 1, message = "Thứ tự hiển thị bắt đầu từ 1")
        @Schema(description = "Bỏ trống thì là 1", example = "1")
        Integer sortOrder) {
}
