package com.ridebooking.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình JWT, khai báo trong application.properties:
 * {@code app.jwt.secret}, {@code app.jwt.expiration-minutes}, {@code app.jwt.issuer}.
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Khóa ký HMAC-SHA, tối thiểu 32 ký tự. Không đưa secret thật lên git. */
    private String secret = "ride-booking-microservices-dev-secret-key-change-me-0123456789";

    /** Hạn access token. Ngắn để giảm rủi ro lộ token, dài hơn sẽ phải refresh. */
    private long expirationMinutes = 15;

    private String issuer = "ride-booking-auth-service";

    /** Hạn refresh token (ngày). */
    private long refreshTokenDays = 7;

    public long getRefreshTokenDays() {
        return refreshTokenDays;
    }

    public void setRefreshTokenDays(long refreshTokenDays) {
        this.refreshTokenDays = refreshTokenDays;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}