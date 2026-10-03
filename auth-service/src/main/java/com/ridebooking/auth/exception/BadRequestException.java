package com.ridebooking.auth.exception;

/** 400 - yêu cầu không hợp lý về nghiệp vụ (không phải lỗi validate của DTO). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
