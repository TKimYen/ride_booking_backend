package com.ridebooking.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Phiên Stripe Checkout vừa tạo. Client chuyển hướng khách sang {@code checkoutUrl}. */
public record CheckoutSessionResponse(
        @Schema(description = "Mã đơn đặt vé", example = "1")
        Integer bookingId,
        @Schema(description = "ID phiên thanh toán Stripe (cs_...)", example = "cs_test_a1b2c3")
        String sessionId,
        @Schema(description = "URL trang thanh toán Stripe để chuyển hướng khách",
                example = "https://checkout.stripe.com/c/pay/cs_test_a1b2c3")
        String checkoutUrl,
        @Schema(description = "Số tiền", example = "500000")
        BigDecimal amount,
        @Schema(description = "Đơn vị tiền tệ", example = "VND")
        String currency,
        @Schema(description = "Hạn giữ ghế của đơn")
        LocalDateTime expiresAt) {
}
