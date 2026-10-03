package com.ridebooking.payment.exception;

/** Xung đột trạng thái nghiệp vụ (đơn không còn chờ thanh toán) — trả về 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
