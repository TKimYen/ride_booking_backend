package com.ridebooking.auth.exception;

/**
 * 423 - tài khoản đang bị khoá do đăng nhập sai quá nhiều lần.
 * Frontend nên hiển thị thông báo "thử lại sau N phút" thay vì đăng nhập lại liên tục.
 */
public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
