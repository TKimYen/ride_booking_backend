package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body cho {@code POST /api/bookings/lookup} (Trang Tra cứu vé) và
 * {@code POST /api/bookings/claim}: mã vé + số điện thoại liên hệ của đơn.
 */
public record BookingLookupRequest(

        @NotBlank(message = "Mã vé không được bỏ trống")
        @Size(max = 12, message = "Mã vé không hợp lệ")
        @Schema(example = "K7M2Q9XA", requiredMode = Schema.RequiredMode.REQUIRED)
        String bookingCode,

        @NotBlank(message = "Số điện thoại không được bỏ trống")
        @Size(max = 20, message = "Số điện thoại không hợp lệ")
        @Schema(example = "0901234567", requiredMode = Schema.RequiredMode.REQUIRED)
        String phone) {
}
