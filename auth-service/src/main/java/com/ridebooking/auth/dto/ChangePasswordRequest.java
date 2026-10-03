package com.ridebooking.auth.dto;

import com.ridebooking.auth.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Body cho PUT /api/auth/password")
public record ChangePasswordRequest(

        @NotBlank(message = "Mật khẩu cũ không được để trống")
        @Schema(description = "Mật khẩu hiện tại", example = "12345678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @StrongPassword
        @Schema(description = "Mật khẩu mới, 8-72 ký tự, cần cả chữ và số", example = "Moi@2026x",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword) {
}