package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Slug dựng từ tên tiếng Việt — slug đi vào URL nên sai là URL gãy.
 */
class LocationSlugTest {

    @ParameterizedTest
    @CsvSource({
            "Hà Nội, ha-noi",
            "Hồ Chí Minh, ho-chi-minh",
            "Đà Nẵng, da-nang",
            "Bến xe Mỹ Đình, ben-xe-my-dinh",
            "Buôn Ma Thuột, buon-ma-thuot",
            "Nha Trang, nha-trang",
            "Hạ Long, ha-long",
            "Quy Nhơn, quy-nhon",
    })
    @DisplayName("Bỏ dấu tiếng Việt, kể cả chữ đ")
    void slugFromVietnameseName(String name, String expected) {
        assertThat(LocationService.resolveSlug(null, name)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Admin nhập slug thì giữ nguyên slug đó")
    void providedSlugWins() {
        assertThat(LocationService.resolveSlug("  custom-slug  ", "Đà Nẵng")).isEqualTo("custom-slug");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Đà Nẵng", "   Đà Nẵng   "})
    @DisplayName("Slug không có dấu gạch ở đầu/cuối")
    void slugHasNoEdgeDashes(String name) {
        assertThat(LocationService.resolveSlug(null, name)).doesNotStartWith("-").doesNotEndWith("-");
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!!", "   "})
    @DisplayName("Tên không còn chữ latin thì báo lỗi thay vì sinh slug rỗng")
    void unrepresentableNameIsRejected(String name) {
        assertThatThrownBy(() -> LocationService.resolveSlug(null, name))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
