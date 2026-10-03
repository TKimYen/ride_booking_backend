package com.ridebooking.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Kết quả đăng nhập / đăng ký / refresh token")
public record AuthResponse(

        @Schema(description = "JWT ngắn hạn, gửi kèm header Authorization: Bearer <token>")
        String accessToken,

        @Schema(description = "Luôn là Bearer", example = "Bearer")
        String tokenType,

        @Schema(description = "Thời hạn access token (giây)", example = "900")
        long expiresIn,

        @Schema(description = "Dùng để lấy access token mới khi hết hạn. Gửi ở body của POST /api/auth/refresh",
                example = "0kVv...abc")
        String refreshToken,

        @Schema(description = "Thời hạn refresh token (giây)", example = "604800")
        long refreshExpiresIn,

        @Schema(description = "Thời điểm cấp token")
        Instant issuedAt,

        @Schema(description = "Thông tin tài khoản")
        UserResponse user) {

    public static AuthResponse of(String accessToken,
                                  long expiresInSeconds,
                                  String refreshToken,
                                  long refreshExpiresInSeconds,
                                  Instant issuedAt,
                                  UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, refreshToken,
                refreshExpiresInSeconds, issuedAt, user);
    }
}