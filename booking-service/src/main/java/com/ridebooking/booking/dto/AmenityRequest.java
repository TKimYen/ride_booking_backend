package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body thêm/sửa một tiện ích trong danh mục dùng chung. */
public record AmenityRequest(

        @NotBlank(message = "Mã tiện ích không được bỏ trống")
        @Size(max = 40, message = "Mã tiện ích tối đa 40 ký tự")
        @Schema(example = "WIFI", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @NotBlank(message = "Tên tiện ích không được bỏ trống")
        @Size(max = 100, message = "Tên tiện ích tối đa 100 ký tự")
        @Schema(example = "Wi-Fi miễn phí", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 60, message = "Tên icon tối đa 60 ký tự")
        @Schema(description = "Tên icon phía frontend, không phải URL ảnh", example = "wifi")
        String icon) {
}
