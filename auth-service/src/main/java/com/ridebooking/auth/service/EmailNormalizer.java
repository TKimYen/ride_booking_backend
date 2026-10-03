package com.ridebooking.auth.service;

import java.util.Locale;

/**
 * Chuẩn hoá email tại một chỗ duy nhất.
 *
 * <p>Trước đây có hai bản {@code normalizeEmail} cùng tồn tại trong service và
 * lệch nhau: bản trong {@code AuthService} không null-safe (NPE nếu email null),
 * bản trong {@code EmailTokenService} thì trả về {@code ""}. Hai bản cùng dùng
 * {@code toLowerCase()} không chỉ định locale — đó là lỗi Turkish-I: nếu JVM chạy
 * với locale Thổ Nhĩ Kỳ thì {@code "I".toLowerCase()} ra {@code "ı"} (i không dấu),
 * làm hỏng cả địa chỉ email. Luôn dùng {@link Locale#ROOT}.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * Trim + lowercase về dạng so sánh/lưu trữ.
     *
     * @return email đã chuẩn hoá, hoặc {@code null} nếu {@code email} null
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
