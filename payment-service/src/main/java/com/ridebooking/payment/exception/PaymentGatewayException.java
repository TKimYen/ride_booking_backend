package com.ridebooking.payment.exception;

/** Lỗi phía cổng thanh toán (Stripe) hoặc service phụ thuộc — trả về 502. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
