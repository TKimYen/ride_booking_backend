package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Thay toàn bộ tiện ích của một nhà xe. Danh sách rỗng là bỏ hết tiện ích. */
public record CompanyAmenitiesRequest(

        @NotNull(message = "Danh sách tiện ích không được bỏ trống")
        @Size(max = 100, message = "Tối đa 100 tiện ích")
        @Schema(description = "id các tiện ích của nhà xe", example = "[1, 2, 5]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull(message = "id tiện ích không được bỏ trống") Integer> amenityIds) {
}
