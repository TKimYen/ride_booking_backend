package com.ridebooking.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token lấy từ query string của link trong email. */
public record EmailTokenRequest(
        @NotBlank(message = "Thiếu mã xác nhận")
        @Size(max = 200, message = "Mã không hợp lệ")
        String token) {
}
