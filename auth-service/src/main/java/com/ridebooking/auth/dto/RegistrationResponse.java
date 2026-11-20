package com.ridebooking.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tài khoản đã tạo; người dùng cần xác minh email trước khi đăng nhập")
public record RegistrationResponse(
        @Schema(description = "Email vừa đăng ký", example = "user@example.com")
        String email,
        @Schema(description = "Hướng dẫn bước tiếp theo")
        String message) {
}
