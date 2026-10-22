package com.ridebooking.booking.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token chứng minh quyền với đơn của khách vãng lai (db.md mục B8).
 *
 * <p>Token gốc chỉ trả cho khách MỘT lần; database chỉ giữ SHA-256 hex ở
 * {@code bookings.guest_token_hash} — cùng quy ước với {@code refresh_tokens}, để
 * lộ database cũng không mở được đơn của ai.
 */
public final class GuestTokens {

    /** 256 bit: không đoán được, kể cả khi biết mã vé. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private GuestTokens() {
    }

    /** Token mới, dạng base64url không padding (43 ký tự) để gửi được qua header. */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex chữ thường, khớp CHECK {@code ck_bookings_guest_token_hex}. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", ex);
        }
    }

    /**
     * Token có khớp hash đã lưu không. So sánh hằng thời gian để không lộ dần
     * từng ký tự qua thời gian phản hồi.
     */
    public static boolean matches(String token, String storedHash) {
        if (token == null || token.isBlank() || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(token.trim()).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
