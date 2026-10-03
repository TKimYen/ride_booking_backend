package com.ridebooking.payment.client;

import java.math.BigDecimal;

/** Payload gửi booking-service để xác nhận đã thu tiền; khớp {@code InternalPaymentRequest}. */
public record ConfirmPaymentCommand(
        String gateway,
        String transactionNo,
        BigDecimal amount,
        String bankCode,
        String responseCode) {
}
