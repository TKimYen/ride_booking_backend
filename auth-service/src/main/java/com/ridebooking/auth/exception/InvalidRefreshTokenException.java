package com.ridebooking.auth.exception;

/**
 * 401 - refresh token không hợp lệ: sai, hết hạn, đã thu hồi, hoặc bị phát hiện tái sử dụng.
 * Client phải đăng nhập lại từ đầu.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
