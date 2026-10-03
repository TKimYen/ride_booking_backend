package com.ridebooking.auth.exception;

/**
 * 429 - đã yêu cầu gửi quá nhiều email trong ngắn hạn.
 *
 * <p>Chống việc dùng endpoint gửi mail để spam một địa chỉ nạn nhân, và chống
 * việc tự động dò xem email nào có trong hệ thống.
 */
public class TooManyEmailRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyEmailRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
