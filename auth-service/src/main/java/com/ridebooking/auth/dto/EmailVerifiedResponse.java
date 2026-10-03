package com.ridebooking.auth.dto;

import java.time.LocalDateTime;

/** Kết quả xác minh email / các thao tác liên quan tới email. */
public record EmailVerifiedResponse(
        boolean verified,
        LocalDateTime verifiedAt,
        String message) {

    public static EmailVerifiedResponse of(boolean verified, LocalDateTime verifiedAt, String message) {
        return new EmailVerifiedResponse(verified, verifiedAt, message);
    }
}
