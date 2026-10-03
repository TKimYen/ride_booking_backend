package com.ridebooking.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Body cho POST /api/auth/refresh và POST /api/auth/logout")
public record RefreshTokenRequest(

        @NotBlank(message = "refreshToken không được để trống")
        @Schema(description = "refreshToken trả về lúc đăng nhập / đăng ký",
                example = "0kVv...abc", requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken) {
}