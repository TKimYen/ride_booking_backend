package com.ridebooking.auth.exception;

/** 409 - email đã tồn tại trong hệ thống. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email đã được sử dụng: " + email);
    }
}