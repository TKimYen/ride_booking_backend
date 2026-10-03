package com.ridebooking.booking.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tham số {@code sort} tới từ người dùng gõ, nên phải chấp nhận nhiều cách viết.
 */
class TripSortTest {

    @ParameterizedTest
    @CsvSource({
            "priceAsc, PRICE_ASC",
            "price_asc, PRICE_ASC",
            "price-asc, PRICE_ASC",
            "PRICE_ASC, PRICE_ASC",
            "PriceAsc, PRICE_ASC",
            "departureAsc, DEPARTURE_ASC",
            "departureTimeAsc, DEPARTURE_ASC",
            "departureDesc, DEPARTURE_DESC",
            "departureTimeDesc, DEPARTURE_DESC",
            "priceDesc, PRICE_DESC",
            "newest, NEWEST",
    })
    @DisplayName("Chấp nhận camelCase, gạch nối và gạch dưới cho cùng một kiểu sắp xếp")
    void acceptsAllNamingStyles(String raw, TripSort expected) {
        assertThat(TripSort.from(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "rẻ nhất", "1; DROP TABLE trips", "id ASC"})
    @DisplayName("Gõ sai thì rơi về sắp xếp mặc định, không lỗi 400")
    void unknownValueFallsBackToDefault(String raw) {
        assertThat(TripSort.from(raw)).isEqualTo(TripSort.DEPARTURE_ASC);
    }

    @Test
    @DisplayName("Không truyền thì sắp xếp theo giờ đi")
    void nullFallsBackToDepartureAscending() {
        assertThat(TripSort.from(null)).isEqualTo(TripSort.DEPARTURE_ASC);
    }

    @Test
    @DisplayName("Kiểu sắp xếp chỉ dùng tên cột của entity, không dùng chuỗi người dùng gõ")
    void attributeIsAWhitelistedColumn() {
        assertThat(TripSort.PRICE_ASC.attribute()).isEqualTo("basePrice");
        assertThat(TripSort.DEPARTURE_ASC.attribute()).isEqualTo("departureTime");
    }
}