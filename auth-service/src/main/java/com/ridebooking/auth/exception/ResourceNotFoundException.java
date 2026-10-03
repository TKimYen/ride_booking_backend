package com.ridebooking.auth.exception;

/** 404 - không tìm thấy tài khoản. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}