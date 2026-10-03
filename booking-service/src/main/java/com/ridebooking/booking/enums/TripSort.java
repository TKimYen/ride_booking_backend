package com.ridebooking.booking.enums;

import java.util.Arrays;
import java.util.Locale;

/**
 * Cách sắp xếp danh sách chuyến.
 *
 * <p>Đây là <b>allowlist</b> chứ không phải tuỳ chọn. Giá trị sắp xếp đến từ
 * tham số {@code sort} của người dùng; nếu đưa thẳng chuỗi đó vào {@code ORDER BY}
 * thì đó là SQL injection qua đường dẫn không, và chỉ cần một lỗi cấu hình là
 * lộ toàn bộ bảng. Map sang tên cột cố định ở đây thì không có chỗ nào cho người
 * dùng chen ký tự vào.
 */
public enum TripSort {

    /** Mặc định: sớm nhất. */
    DEPARTURE_ASC("departureTime", true),

    /** Muộn nhất. */
    DEPARTURE_DESC("departureTime", false),

    /** Rẻ nhất. */
    PRICE_ASC("basePrice", true),

    /** Đắt nhất. */
    PRICE_DESC("basePrice", false),

    /** Tuyến mới thêm trước. */
    NEWEST("id", false);

    private final String attribute;
    private final boolean ascending;

    TripSort(String attribute, boolean ascending) {
        this.attribute = attribute;
        this.ascending = ascending;
    }

    public String attribute() {
        return attribute;
    }

    public boolean ascending() {
        return ascending;
    }

    /**
     * Đổi chuỗi do người dùng gõ thành enum.
     *
     * <p>Không khớp thì trả {@link #DEPARTURE_ASC} chứ không ném lỗi: đây là
     * tham số tiện ích của trang danh sách, gõ sai thì nhận kết quả mặc định
     * còn hơn thấy lỗi 400 giữa lúc lướt trang.
     */
    public static TripSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEPARTURE_ASC;
        }
        // Chấp nhận cả ba cách viết: price_asc, price-asc và priceAsc. Chỉ chuẩn hoá
        // gạch nối thì "priceAsc" hoá PRICEASC, không khớp PRICE_ASC, và người gọi
        // gõ đúng theo tài liệu API vẫn bị rơi về sắp xếp mặc định mà không có lỗi nào
        // báo cho họ biết.
        String camelSplit = raw.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace("-", "_")
                .toUpperCase(Locale.ROOT);
        // "departureTimeAsc" và "departureAsc" là cùng một kiểu. Không bỏ token TIME
        // thì departureTimeDesc rơi về mặc định DEPARTURE_ASC — sai cả chiều sắp xếp.
        String normalized = camelSplit.replace("DEPARTURE_TIME_", "DEPARTURE_");
        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElse(DEPARTURE_ASC);
    }
}