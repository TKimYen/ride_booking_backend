package com.ridebooking.auth.dto;

import com.ridebooking.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Đặt mật khẩu mới bằng link trong email. */
public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu mã xác nhận")
        String token,

        @StrongPassword
        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải dài 8-72 ký tự")
        String newPassword) {
}
