package com.ridebooking.booking.util;

/** Escape ký tự đặc biệt của SQL LIKE, dùng cho mọi từ khoá do người dùng gõ. */
public final class LikePattern {

    private LikePattern() {
    }

    /**
     * Bọc ký tự {@code %}, {@code _} và {@code \} bằng dấu gạch chéo.
     *
     * <p>Không escape thì gõ {@code %} trong ô tìm kiếm sẽ khớp mọi dòng, còn
     * {@code _} khớp bất kỳ ký tự nào. Không phải lỗ hổng (truy vấn vẫn là
     * tham số hoá, không nối chuỗi), nhưng gợi ý sai lệch thì người dùng tưởng
     * chức năng tìm kiếm hỏng.
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
