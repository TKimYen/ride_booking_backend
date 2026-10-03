package com.ridebooking.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình client gọi API nội bộ của booking-service.
 *
 * <p>{@code internalKey} phải trùng {@code app.internal.key} của booking-service;
 * Docker Compose cấp chung biến {@code INTERNAL_API_KEY}.
 */
@ConfigurationProperties(prefix = "app.clients.booking-service")
public class BookingClientProperties {

    private String baseUrl = "http://localhost:8083";

    private long timeoutMs = 1500;

    private String internalKey = "ride-booking-internal-dev-key";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getInternalKey() {
        return internalKey;
    }

    public void setInternalKey(String internalKey) {
        this.internalKey = internalKey;
    }
}
