package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body cho POST/PUT {@code /api/admin/locations}. */
public record LocationRequest(

        @NotBlank(message = "Tên địa điểm không được để trống")
        @Size(max = 100, message = "Tên địa điểm không được vượt quá 100 ký tự")
        @Schema(description = "Tên tỉnh/thành phố", example = "Hà Giang", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Slug cho URL. Bỏ trống thì tự sinh từ tên")
        @Size(max = 120, message = "Slug không được vượt quá 120 ký tự")
        @Pattern(regexp = "^[a-z0-9-]*$",
                message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang")
        String slug) {
}
