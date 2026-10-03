package com.ridebooking.payment.exception;

/** Dữ liệu yêu cầu sai về nghiệp vụ — trả về 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
