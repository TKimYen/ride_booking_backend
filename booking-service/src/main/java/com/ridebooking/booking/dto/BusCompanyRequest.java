package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body cho POST/PUT {@code /api/admin/bus-companies}. */
public record BusCompanyRequest(

        @NotBlank(message = "Tên nhà xe không được để trống")
        @Size(max = 150, message = "Tên nhà xe không được vượt quá 150 ký tự")
        @Schema(description = "Tên nhà xe", example = "G8 SAPA OPEN TOUR", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
        @Schema(description = "Mô tả hiển thị ở Trang chi tiết")
        String description,

        @Size(max = 500, message = "Đường dẫn logo không được vượt quá 500 ký tự")
        @Schema(description = "URL logo nhà xe")
        String logoUrl) {
}
