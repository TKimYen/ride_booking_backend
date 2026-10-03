package com.ridebooking.auth.exception;

/**
 * 400 - mã trong link (xác minh email, đặt lại mật khẩu, đổi email) không hợp lệ,
 * đã dùng, hoặc đã hết hạn.
 *
 * <p>Thông điệp cố tình không nói rõ lý do thật, và luôn giống nhau giữa "không
 * tồn tại" và "đã dùng", để không tạo điều kiện dò mã hợp lệ.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
