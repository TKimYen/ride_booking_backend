package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.dto.TripCancellationResponse;
import com.ridebooking.booking.dto.TripCompletionResponse;
import com.ridebooking.booking.dto.TripRequest;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Hoàn thành chuyến [3.1] và huỷ chuyến [3.2], chạy thật trên H2. */
@SpringBootTest
@Transactional
class TripLifecycleTest {

    private static final int USER = 101;

    @Autowired private TripLifecycleService tripLifecycleService;
    @Autowired private TripAdminService tripAdminService;
    @Autowired private BookingService bookingService;
    @Autowired private SeatGenerator seatGenerator;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private LocationRepository locationRepository;

    private Route route;
    private BusCompany company;

    @BeforeEach
    void setUp() {
        Location from = locationRepository.save(location("Điểm đi lifecycle", "diem-di-lifecycle"));
        Location to = locationRepository.save(location("Điểm đến lifecycle", "diem-den-lifecycle"));
        BusCompany newCompany = new BusCompany();
        newCompany.setName("Nhà xe lifecycle");
        company = busCompanyRepository.save(newCompany);

        Route newRoute = new Route();
        newRoute.setName("Tuyến lifecycle");
        newRoute.setOrigin(from);
        newRoute.setDestination(to);
        route = routeRepository.save(newRoute);
    }

    // ---- [3.1] Tự hoàn thành ----

    @Test
    @DisplayName("Job: chuyến đã đến nơi thành COMPLETED, đơn PAID thành COMPLETED")
    void sweepCompletesArrivedTripAndPaidBookings() {
        Trip trip = futureTrip();
        Integer paid = holdAndPay(trip, 0);
        Integer pending = hold(trip, 1).id();
        moveToPast(trip);

        TripLifecycleService.CompletionSweep sweep = tripLifecycleService.completeArrivedTrips(LocalDateTime.now());

        assertThat(sweep.trips()).isEqualTo(1);
        assertThat(sweep.bookings()).isEqualTo(1);
        assertThat(tripStatus(trip)).isEqualTo(TripStatus.COMPLETED);
        assertThat(bookingStatus(paid)).isEqualTo(BookingStatus.COMPLETED);
        // Đơn chưa trả tiền không thành "đã đi": job hết hạn sẽ xử lý nó.
        assertThat(bookingStatus(pending)).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("Job: bỏ qua chuyến chưa đến nơi và chuyến đã huỷ")
    void sweepLeavesOtherTripsAlone() {
        Trip upcoming = futureTrip();
        Integer upcomingBooking = holdAndPay(upcoming, 0);

        Trip cancelled = futureTrip();
        Integer cancelledBooking = holdAndPay(cancelled, 0);
        tripLifecycleService.cancel(cancelled.getId());
        moveToPast(tripRepository.findById(cancelled.getId()).orElseThrow());

        TripLifecycleService.CompletionSweep sweep = tripLifecycleService.completeArrivedTrips(LocalDateTime.now());

        assertThat(sweep.trips()).isZero();
        assertThat(tripStatus(upcoming)).isEqualTo(TripStatus.ACTIVE);
        assertThat(bookingStatus(upcomingBooking)).isEqualTo(BookingStatus.PAID);
        assertThat(tripStatus(cancelled)).isEqualTo(TripStatus.CANCELLED);
        assertThat(bookingStatus(cancelledBooking)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Job: đơn thanh toán sau khi Admin bấm hoàn thành vẫn được quét ở lần sau")
    void sweepCatchesBookingPaidAfterManualCompletion() {
        Trip trip = futureTrip();
        Integer booking = hold(trip, 0).id();
        moveDepartureToPast(trip);
        tripLifecycleService.complete(trip.getId());

        bookingService.pay(USER, booking, new PaymentRequest("VNPAY", null, null, null));
        assertThat(bookingStatus(booking)).isEqualTo(BookingStatus.PAID);

        tripLifecycleService.completeArrivedTrips(LocalDateTime.now());

        assertThat(bookingStatus(booking)).isEqualTo(BookingStatus.COMPLETED);
    }

    // ---- [3.1] Admin bấm hoàn thành ----

    @Test
    @DisplayName("Admin hoàn thành chuyến đã khởi hành; gọi lại không lỗi và không đếm lại")
    void manualCompleteIsIdempotent() {
        Trip trip = futureTrip();
        Integer booking = holdAndPay(trip, 0);
        moveDepartureToPast(trip);

        TripCompletionResponse first = tripLifecycleService.complete(trip.getId());
        TripCompletionResponse second = tripLifecycleService.complete(trip.getId());

        assertThat(first.trip().status()).isEqualTo("COMPLETED");
        assertThat(first.completedBookings()).isEqualTo(1);
        assertThat(second.completedBookings()).isZero();
        assertThat(bookingStatus(booking)).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("Không hoàn thành được chuyến chưa khởi hành")
    void cannotCompleteBeforeDeparture() {
        Trip trip = futureTrip();

        assertThatThrownBy(() -> tripLifecycleService.complete(trip.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("chưa khởi hành");
    }

    @Test
    @DisplayName("Không hoàn thành được chuyến đã huỷ")
    void cannotCompleteCancelledTrip() {
        Trip trip = futureTrip();
        tripLifecycleService.cancel(trip.getId());

        assertThatThrownBy(() -> tripLifecycleService.complete(trip.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã huỷ");
    }

    // ---- [3.2] Huỷ chuyến ----

    @Test
    @DisplayName("Huỷ chuyến: trả mọi ghế, huỷ đơn, chỉ đơn đã trả tiền nằm trong danh sách hoàn")
    void cancelReleasesSeatsAndListsRefunds() {
        Trip trip = futureTrip();
        Integer paid = holdAndPay(trip, 0);
        Integer pending = hold(trip, 1).id();

        TripCancellationResponse result = tripLifecycleService.cancel(trip.getId());

        assertThat(result.trip().status()).isEqualTo("CANCELLED");
        assertThat(result.cancelledBookings()).isEqualTo(2);
        assertThat(result.releasedSeats()).isEqualTo(2);
        assertThat(bookingStatus(paid)).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingStatus(pending)).isEqualTo(BookingStatus.CANCELLED);
        assertThat(seatsOf(trip)).allMatch(seat -> seat.getStatus() == SeatStatus.AVAILABLE
                && seat.getHoldingUntil() == null);

        assertThat(result.refunds()).hasSize(1);
        TripCancellationResponse.RefundItem refund = result.refunds().getFirst();
        assertThat(refund.bookingId()).isEqualTo(paid);
        assertThat(refund.gateway()).isEqualTo("VNPAY");
        assertThat(refund.transactionNo()).isNotBlank();
        assertThat(refund.amount()).isPositive();
        assertThat(result.totalRefundAmount()).isEqualByComparingTo(refund.amount());
    }

    @Test
    @DisplayName("Huỷ chuyến lần hai không lỗi và vẫn trả đủ danh sách hoàn tiền")
    void cancelTwiceStillReturnsRefunds() {
        Trip trip = futureTrip();
        holdAndPay(trip, 0);

        tripLifecycleService.cancel(trip.getId());
        TripCancellationResponse second = tripLifecycleService.cancel(trip.getId());

        assertThat(second.cancelledBookings()).isZero();
        assertThat(second.releasedSeats()).isZero();
        assertThat(second.refunds()).hasSize(1);
    }

    @Test
    @DisplayName("Không huỷ được chuyến đã hoàn thành -> 409")
    void cannotCancelCompletedTrip() {
        Trip trip = futureTrip();
        moveDepartureToPast(trip);
        tripLifecycleService.complete(trip.getId());

        assertThatThrownBy(() -> tripLifecycleService.cancel(trip.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã hoàn thành");
    }

    @Test
    @DisplayName("Không huỷ được chuyến đã qua giờ đến dù job chưa kịp quét")
    void cannotCancelArrivedTripBeforeSweep() {
        Trip trip = futureTrip();
        moveToPast(trip);

        assertThatThrownBy(() -> tripLifecycleService.cancel(trip.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(tripStatus(trip)).isEqualTo(TripStatus.ACTIVE);
    }

    @Test
    @DisplayName("Sau khi huỷ chuyến: không đặt được vé, thanh toán đơn cũ bị từ chối")
    void cancelledTripRejectsBookingAndPayment() {
        Trip trip = futureTrip();
        Integer pending = hold(trip, 0).id();

        tripLifecycleService.cancel(trip.getId());

        assertThatThrownBy(() -> hold(trip, 1)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> bookingService.pay(USER, pending, new PaymentRequest("VNPAY", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Chuyến đã huỷ còn đơn thì không xoá được, trả 409 thay vì lỗi khoá ngoại")
    void cannotDeleteCancelledTripWithBookings() {
        Trip trip = futureTrip();
        holdAndPay(trip, 0);
        tripLifecycleService.cancel(trip.getId());

        assertThatThrownBy(() -> tripAdminService.delete(trip.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã có đơn");
    }

    // ---- Không đi đường vòng qua API sửa ----

    @Test
    @DisplayName("API sửa không đổi được trạng thái, nên không bỏ qua bước trả ghế")
    void updateCannotChangeStatus() {
        Trip trip = futureTrip();
        TripRequest request = updateRequest(trip, "CANCELLED");

        assertThatThrownBy(() -> tripAdminService.update(trip.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("/cancel");
        assertThat(tripStatus(trip)).isEqualTo(TripStatus.ACTIVE);
    }

    @Test
    @DisplayName("Sửa chuyến đã huỷ mà bỏ trống status thì không mở bán lại")
    void updateWithoutStatusKeepsCancelled() {
        Trip trip = futureTrip();
        tripLifecycleService.cancel(trip.getId());

        tripAdminService.update(trip.getId(), updateRequest(tripRepository.findById(trip.getId()).orElseThrow(), null));

        assertThat(tripStatus(trip)).isEqualTo(TripStatus.CANCELLED);
    }

    // ---- Dựng dữ liệu ----

    private Trip futureTrip() {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setCompany(company);
        trip.setBusType(BusType.LIMOUSINE_29);
        trip.setDepartureTime(LocalDateTime.now().plusDays(7));
        trip.setArrivalTime(LocalDateTime.now().plusDays(7).plusHours(8));
        trip.setBasePrice(new BigDecimal("500000"));
        trip.setStatus(TripStatus.ACTIVE);
        trip = tripRepository.saveAndFlush(trip);
        tripSeatRepository.saveAll(seatGenerator.generate(trip));
        return trip;
    }

    /** Xe đã chạy và đã tới nơi. */
    private void moveToPast(Trip trip) {
        trip.setDepartureTime(LocalDateTime.now().minusHours(10));
        trip.setArrivalTime(LocalDateTime.now().minusHours(2));
        tripRepository.saveAndFlush(trip);
    }

    /** Xe đã chạy nhưng chưa tới nơi. */
    private void moveDepartureToPast(Trip trip) {
        trip.setDepartureTime(LocalDateTime.now().minusHours(1));
        trip.setArrivalTime(LocalDateTime.now().plusHours(5));
        tripRepository.saveAndFlush(trip);
    }

    private BookingResponse hold(Trip trip, int seatIndex) {
        Integer seatId = seatsOf(trip).get(seatIndex).getId();
        return bookingService.holdSeats(USER, new HoldSeatsRequest(trip.getId(), List.of(seatId), null, null,
                "Khách kiểm thử", "0901234567", "khach@example.com"));
    }

    private Integer holdAndPay(Trip trip, int seatIndex) {
        Integer id = hold(trip, seatIndex).id();
        bookingService.pay(USER, id, new PaymentRequest("VNPAY", null, null, null));
        return id;
    }

    private List<TripSeat> seatsOf(Trip trip) {
        return tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId());
    }

    private TripStatus tripStatus(Trip trip) {
        return tripRepository.findStatusById(trip.getId()).orElseThrow();
    }

    private BookingStatus bookingStatus(Integer id) {
        return bookingRepository.findById(id).orElseThrow().getStatus();
    }

    private TripRequest updateRequest(Trip trip, String status) {
        return new TripRequest(route.getId(), company.getId(), trip.getBusType().name(),
                trip.getDepartureTime(), trip.getArrivalTime(), trip.getBasePrice(), status);
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }
}
