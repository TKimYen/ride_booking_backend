package com.ridebooking.user.exception;

/** 409 - email đã tồn tại. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email đã được sử dụng: " + email);
    }
}