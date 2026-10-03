package com.ridebooking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Yêu cầu gửi link đặt lại mật khẩu. */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        String email) {
    /** Trim {@code email} trước khi Bean Validation chạy — xem {@link RegisterRequest}. */

    public ForgotPasswordRequest {
        email = email == null ? null : email.trim();
    }
}
