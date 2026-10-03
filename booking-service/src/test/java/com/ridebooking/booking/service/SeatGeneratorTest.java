package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Ghế sinh ra phải khớp sơ đồ loại xe và sẵn sàng cho khách đặt.
 *
 * <p>Đây là ranh giới quan trọng nhất của booking: ghế sai ở đây thì mọi thứ phía
 * sau (giữ ghế, thanh toán) chạy đúng trên dữ liệu sai.
 */
class SeatGeneratorTest {

    private final SeatGenerator seatGenerator = new SeatGenerator();

    private Trip tripWith(BusType busType) {
        Trip trip = new Trip();
        trip.setRoute(new Route());
        trip.setBusType(busType);
        trip.setBasePrice(new BigDecimal("250000"));
        trip.setDepartureTime(LocalDateTime.of(2026, 10, 5, 6, 0));
        trip.setArrivalTime(LocalDateTime.of(2026, 10, 5, 12, 0));
        trip.setStatus(TripStatus.ACTIVE);
        return trip;
    }

    @ParameterizedTest
    @EnumSource(BusType.class)
    @DisplayName("Sinh đúng số ghế mà loại xe khai")
    void generatesDeclaredSeatCount(BusType busType) {
        assertThat(seatGenerator.generate(tripWith(busType))).hasSize(busType.seatCount());
    }

    @ParameterizedTest
    @EnumSource(BusType.class)
    @DisplayName("Ghế mới đều trống, chưa ai giữ")
    void newSeatsAreAllAvailable(BusType busType) {
        List<TripSeat> seats = seatGenerator.generate(tripWith(busType));

        assertThat(seats).allSatisfy(seat -> {
            assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(seat.getHoldingUntil()).isNull();
        });
    }

    @ParameterizedTest
    @EnumSource(BusType.class)
    @DisplayName("Tên ghế không trùng, vì DB có khoá duy nhất theo (trip_id, seat_name)")
    void seatNamesAreUnique(BusType busType) {
        Set<String> names = seatGenerator.generate(tripWith(busType)).stream()
                .map(TripSeat::getSeatName)
                .collect(Collectors.toSet());

        assertThat(names).hasSize(busType.seatCount());
    }

    @Test
    @DisplayName("Xe hai tầng có ghế ở cả hai tầng, tên ghác biệt tầng")
    void twoFloorBusHasSeatsOnBothFloors() {
        List<TripSeat> seats = seatGenerator.generate(tripWith(BusType.LIMOUSINE_29));

        Set<Integer> floors = seats.stream().map(TripSeat::getFloor).collect(Collectors.toSet());
        assertThat(floors).containsExactlyInAnyOrder(1, 2);
        assertThat(seats).allSatisfy(seat ->
                assertThat(seat.getSeatName()).startsWith(String.valueOf(seat.getFloor())));
    }

    @Test
    @DisplayName("Ghế thuộc chuyến được sinh, và ghế đắt hơn cộng đúng phần chênh lệch")
    void seatsBelongToTripAndPriceAddsSurcharge() {
        Trip trip = tripWith(BusType.CABIN_DOUBLE_22);
        List<TripSeat> seats = seatGenerator.generate(trip);

        assertThat(seats).allSatisfy(seat -> assertThat(seat.getTrip()).isSameAs(trip));

        TripSeat doubleSeat = seats.get(0);
        assertThat(doubleSeat.getExtraPrice()).isEqualByComparingTo(BusType.DOUBLE_SURCHARGE);
        assertThat(doubleSeat.effectivePrice(trip.getBasePrice()))
                .isEqualByComparingTo(trip.getBasePrice().add(BusType.DOUBLE_SURCHARGE));
    }

    @Test
    @DisplayName("Ghế đơn không cộng thêm tiền")
    void singleSeatHasNoSurcharge() {
        Trip trip = tripWith(BusType.LIMOUSINE_29);

        TripSeat single = seatGenerator.generate(trip).stream()
                .filter(seat -> BusType.SeatType.SINGLE.equals(seat.getSeatType()))
                .findFirst()
                .orElseThrow();

        assertThat(single.getExtraPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(single.effectivePrice(trip.getBasePrice())).isEqualByComparingTo(trip.getBasePrice());
    }

    @Test
    @DisplayName("Ghế sát lối đi đắt hơn ghế sát cửa sổ")
    void aisleSeatCostsMoreThanWindowSeat() {
        Trip trip = tripWith(BusType.LIMOUSINE_29);

        BigDecimal aisle = priceOfSeat(trip, "1A2");
        BigDecimal window = priceOfSeat(trip, "1A1");

        assertThat(aisle).isGreaterThan(window);
        // Chênh lệch phải đúng bằng phần cộng khai báo, không phải một con số tùy ý.
        assertThat(aisle.subtract(window)).isEqualByComparingTo(BusType.DOUBLE_AISLE_EXTRA);
    }

    private BigDecimal priceOfSeat(Trip trip, String seatName) {
        return seatGenerator.generate(trip).stream()
                .filter(seat -> seatName.equals(seat.getSeatName()))
                .findFirst()
                .orElseThrow()
                .effectivePrice(trip.getBasePrice());
    }
}