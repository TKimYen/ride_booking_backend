package com.ridebooking.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Xác nhận đổi email bằng link gửi tới email mới. */
public record ConfirmEmailChangeRequest(
        @NotBlank(message = "Thiếu mã xác nhận")
        String token,

        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword) {
}
