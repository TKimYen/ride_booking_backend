package com.ridebooking.booking.util;

import java.security.SecureRandom;

/** Sinh mã vé {@code bookings.booking_code} hiển thị cho khách, ví dụ {@code K7M2Q9XA}. */
public final class BookingCodeGenerator {

    /** Bỏ 0/O và 1/I: khách đọc mã qua điện thoại hay gõ lại hay nhầm các cặp này. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingCodeGenerator() {
    }

    /**
     * 32^8 ≈ 1,1 nghìn tỷ tổ hợp nên trùng gần như không xảy ra; nếu có, UNIQUE
     * {@code uk_bookings_code} chặn lại. Dùng {@link SecureRandom} vì mã vé là một
     * nửa thông tin tra cứu (cùng SĐT), không được đoán trước.
     */
    public static String next() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
