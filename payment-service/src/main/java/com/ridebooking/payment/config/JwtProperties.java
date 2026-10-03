package com.ridebooking.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình JWT. PHẢI khớp với auth-service (cùng secret + cùng issuer) để
 * payment-service validate token do auth-service cấp, y như booking-service.
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "ride-booking-microservices-dev-secret-key-change-me-0123456789";

    private String issuer = "ride-booking-auth-service";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
