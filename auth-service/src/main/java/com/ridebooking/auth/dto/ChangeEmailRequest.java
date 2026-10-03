package com.ridebooking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bắt đầu đổi email — cần mật khẩu hiện tại để chứng minh mình là chủ tài khoản. */
public record ChangeEmailRequest(
        @NotBlank(message = "Email mới không được để trống")
        @Email(message = "Email mới không hợp lệ")
        String newEmail,

        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword) {

    /**
     * Trim {@code newEmail} trước khi validate — xem giải thích ở
     * {@link RegisterRequest}. Không trim {@code currentPassword}.
     */
    public ChangeEmailRequest {
        newEmail = newEmail == null ? null : newEmail.trim();
    }
}
