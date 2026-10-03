package com.ridebooking.auth.dto;

/**
 * Phản hồi chung cho các endpoint gửi email tự động.
 *
 * <p>Message luôn giống nhau dù email có tồn tại, đã xác minh hay không — nếu
 * phân biệt, chính endpoint này lộ ra danh sách email đã đăng ký.
 */
public record EmailResponse(String message) {

    public static EmailResponse of(String message) {
        return new EmailResponse(message);
    }
}
