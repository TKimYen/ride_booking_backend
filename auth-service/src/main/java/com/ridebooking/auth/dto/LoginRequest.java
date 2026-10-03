package com.ridebooking.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Body cho POST /api/auth/login")
public record LoginRequest(

        @NotBlank(message = "Email không được để trống")
        @Schema(description = "Email đã đăng ký", example = "huy@test.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Schema(description = "Mật khẩu", example = "12345678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password) {

    /**
     * Login cũng trim email: người dùng dán email có space vẫn phải đăng nhập được.
     * TUYỆT ĐỐI không trim {@code password} — khoảng trắng là một phần của bí mật,
     * cắt đi sẽ khiến mật khẩu gõ ra khác mật khẩu đã đăng ký.
     */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
