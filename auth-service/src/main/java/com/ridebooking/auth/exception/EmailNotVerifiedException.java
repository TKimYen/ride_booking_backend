package com.ridebooking.auth.exception;

/**
 * 403 - tài khoản tồn tại nhưng email chưa được xác minh, chưa đủ điều kiện dùng
 * tính năng yêu cầu email đã xác minh.
 *
 * <p>Áp dụng cho tính năng quên mật khẩu: nếu ai đó biết email của bạn mà đăng ký
 * trước, bấm "quên mật khẩu" thì sẽ chiếm được tài khoản. Bắt xác minh trước chặn
 * việc đó.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
