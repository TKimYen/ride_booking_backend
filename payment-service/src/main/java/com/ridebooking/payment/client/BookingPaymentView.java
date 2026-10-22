package com.ridebooking.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bản sao tối thiểu của {@code InternalBookingResponse} bên booking-service.
 *
 * <p>Cố ý KHÔNG chia sẻ class giữa hai service: payment-service là module độc lập
 * trong repo, không phụ thuộc artifact của booking-service. Đổi lại phải khai
 * {@link JsonIgnoreProperties} để booking-service thêm field mới không làm vỡ
 * client này.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingPaymentView(
        Integer id,
        Integer userId,
        BigDecimal totalPrice,
        String currency,
        String status,
        LocalDateTime expiresAt,
        boolean payable,
        // true = đơn của khách vãng lai và X-Guest-Token chuyển tiếp khớp đơn.
        // Boolean (không phải boolean): booking-service bản cũ không gửi field này -> null = false.
        Boolean guestAccess) {

    public boolean hasGuestAccess() {
        return Boolean.TRUE.equals(guestAccess);
    }
}
