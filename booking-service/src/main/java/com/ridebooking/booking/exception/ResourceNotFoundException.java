package com.ridebooking.booking.exception;

/** 404 - không tìm thấy tài nguyên. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}