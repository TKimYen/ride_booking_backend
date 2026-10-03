package com.ridebooking.booking.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Sơ đồ ghế phải khớp tên loại xe và không trùng tên.
 *
 * <p>Đã xảy ra: enum tên {@code LIMOUSINE_29} nhưng layout() sinh 49 ghế; và xe hai
 * tầng đặt trùng tên ghế giữa tầng 1 với tầng 2, vi phạm
 * {@code uk_trip_seats_trip_name}. Cả hai đều lộ ra muộn — một cái khi khách đặt
 * nhầm chỗ, một cái khi tạo chuyến thứ hai cùng loại xe.
 */
class BusTypeLayoutTest {

    @Test
    @DisplayName("Mọi loại xe đều tự kiểm tra sơ đồ: đủ số ghế, tên không trùng, tầng hợp lệ")
    void everyLayoutIsSelfConsistent() {
        assertThat(BusType.verifyLayoutsAreConsistent()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(BusType.class)
    @DisplayName("layout() sinh đúng số ghế như tên loại xe khai")
    void layoutSizeMatchesDeclaredSeatCount(BusType type) {
        assertThat(type.layout()).hasSize(type.seatCount());
    }

    @ParameterizedTest
    @EnumSource(BusType.class)
    @DisplayName("Tên ghế là duy nhất trong cả chuyến, kể cả giữa hai tầng")
    void seatNamesAreUniquePerTrip(BusType type) {
        List<BusType.SeatSpec> layout = type.layout();
        Set<String> names = layout.stream().map(BusType.SeatSpec::name).collect(Collectors.toSet());

        assertThat(names).hasSize(layout.size());
    }

    @ParameterizedTest
    @EnumSource(BusType.class)
    @DisplayName("Không ghế nào nằm ngoài số tầng của xe")
    void everySeatIsWithinDeclaredFloors(BusType type) {
        assertThat(type.layout())
                .allSatisfy(seat -> assertThat(seat.floor()).isBetween(1, type.floors()));
    }

    @ParameterizedTest
    @EnumSource(BusType.class)
    @DisplayName("Tên ghế vừa cột VARCHAR(10) của DB")
    void seatNameFitsColumn(BusType type) {
        assertThat(type.layout()).allSatisfy(seat -> assertThat(seat.name()).hasSizeLessThanOrEqualTo(10));
    }

    @Test
    @DisplayName("Xe hai tầng ghi số tầng vào tên ghế")
    void multiFloorBusesPrefixFloorInSeatName() {
        for (BusType type : BusType.values()) {
            if (type.floors() > 1) {
                assertThat(type.layout())
                        .as("%s là xe %d tầng nên tên ghế phải phân biệt được tầng", type, type.floors())
                        .filteredOn(seat -> seat.name().startsWith(String.valueOf(seat.floor())))
                        .hasSize(type.seatCount());
            }
        }
    }
}
