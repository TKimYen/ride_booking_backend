package com.ridebooking.auth.exception;

/** 401 - thông tin đăng nhập sai hoặc token không hợp lệ. */
public class BadCredentialsException extends RuntimeException {

    public BadCredentialsException(String message) {
        super(message);
    }
}