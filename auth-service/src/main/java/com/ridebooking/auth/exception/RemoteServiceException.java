package com.ridebooking.auth.exception;

/** Lỗi khi gọi service khác (user-service) thất bại. */
public class RemoteServiceException extends RuntimeException {

    public RemoteServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}