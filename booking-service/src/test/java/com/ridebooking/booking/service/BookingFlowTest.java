package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.PaymentStatus;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.BadRequestException;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.repository.BookingDetailRepository;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.PaymentRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Luồng đặt vé chạy thật trên H2: giữ ghế, thanh toán, huỷ, hết hạn.
 *
 * <p>Không mock repository vì thứ cần kiểm chứng nằm chính ở tầng dữ liệu — trạng
 * thái ghế sau mỗi thao tác và ràng buộc giá/đơn.
 */
@SpringBootTest
@Transactional
class BookingFlowTest {

    private static final int USER = 101;
    private static final int OTHER_USER = 202;

    @Autowired private BookingService bookingService;

    private BookingExpiryJob bookingExpiryJob;

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingDetailRepository bookingDetailRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private SeatGenerator seatGenerator;

    private Trip trip;

    @BeforeEach
    void setUp() {
        // Dựng thẳng job vì bean bị tắt trong test (app.booking.expiry-job.enabled=false).
        // Trong transaction của test, gọi trực tiếp vẫn chạy đúng hai câu UPDATE tập hợp.
        bookingExpiryJob = new BookingExpiryJob(tripSeatRepository, bookingRepository);

        paymentRepository.deleteAll();
        bookingDetailRepository.deleteAll();
        bookingRepository.deleteAll();
        tripSeatRepository.deleteAll();
        tripRepository.deleteAll();
        routeRepository.deleteAll();
        busCompanyRepository.deleteAll();
        locationRepository.deleteAll();

        Location haNoi = locationRepository.save(location("Hà Nội", "ha-noi"));
        Location saPa = locationRepository.save(location("Sa Pa", "sa-pa"));

        BusCompany company = new BusCompany();
        company.setName("Nhà xe test");
        company = busCompanyRepository.save(company);

        Route route = new Route();
        route.setName("Hà Nội - Sa Pa");
        route.setOrigin(haNoi);
        route.setDestination(saPa);
        route.setDistanceKm(new BigDecimal("465"));
        route.setDurationMinutes(510);
        route = routeRepository.save(route);

        trip = new Trip();
        trip.setRoute(route);
        trip.setCompany(company);
        trip.setBusType(BusType.LIMOUSINE_29);
        trip.setDepartureTime(LocalDateTime.of(2026, 10, 5, 6, 0));
        trip.setArrivalTime(LocalDateTime.of(2026, 10, 5, 14, 30));
        trip.setBasePrice(new BigDecimal("500000"));
        trip.setStatus(TripStatus.ACTIVE);
        trip = tripRepository.saveAndFlush(trip);
        tripSeatRepository.saveAll(seatGenerator.generate(trip));
    }


    @Test
    @DisplayName("Giữ ghế tạo đơn PENDING, ghế HOLDING kèm hạn, tổng giá bằng tổng ghế")
    void holdSeatsCreatesPendingBooking() {
        List<TripSeat> seats = seats();

        BookingResponse response = bookingService.holdSeats(USER,
                holdRequest(seats.get(0).getId(), seats.get(1).getId()));

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING.name());
        assertThat(response.seats()).hasSize(2);
        assertThat(response.totalPrice())
                .isEqualByComparingTo(seats.get(0).effectivePrice(trip.getBasePrice())
                        .add(seats.get(1).effectivePrice(trip.getBasePrice())));
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.holdSecondsRemaining()).isPositive();

        List<TripSeat> after = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId());
        assertThat(after).filteredOn(seat -> seat.getStatus() == SeatStatus.HOLDING)
                .hasSize(2)
                .allSatisfy(seat -> assertThat(seat.getHoldingUntil()).isNotNull());
    }

    @Test
    @DisplayName("Giữ ghế đã có người giữ thì 409 và không đổi gì")
    void cannotHoldSeatAlreadyHeld() {
        TripSeat seat = seats().get(0);
        bookingService.holdSeats(USER, holdRequest(seat.getId()));

        assertThatThrownBy(() -> bookingService.holdSeats(OTHER_USER, holdRequest(seat.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(seat.getSeatName());
    }

    @Test
    @DisplayName("Ghế HOLDING đã quá hạn được giành lại ngay, không chờ job")
    void expiredHoldCanBeReclaimedImmediately() {
        TripSeat seat = seats().get(0);
        bookingService.holdSeats(USER, holdRequest(seat.getId()));

        TripSeat held = tripSeatRepository.findById(seat.getId()).orElseThrow();
        held.setHoldingUntil(LocalDateTime.now().minusMinutes(1));
        tripSeatRepository.saveAndFlush(held);

        BookingResponse reclaimed = bookingService.holdSeats(OTHER_USER, holdRequest(seat.getId()));

        assertThat(reclaimed.status()).isEqualTo(BookingStatus.PENDING.name());
        assertThat(reclaimed.userId()).isEqualTo(OTHER_USER);
    }

    @Test
    @DisplayName("Ghế của chuyến khác thì 400")
    void seatFromAnotherTripRejected() {
        Trip other = createOtherTrip();
        TripSeat foreignSeat = tripSeatRepository
                .findByTripIdOrderByFloorAscSeatNameAsc(other.getId()).get(0);

        assertThatThrownBy(() -> bookingService.holdSeats(USER, holdRequest(foreignSeat.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Quá 10 ghế một đơn thì 400")
    void tooManySeatsRejected() {
        List<Integer> ids = seats().stream().limit(11).map(TripSeat::getId).toList();
        HoldSeatsRequest request = new HoldSeatsRequest(trip.getId(), ids, null, null,
                "Nguyễn Văn A", "0901234567", "a@example.com");

        assertThatThrownBy(() -> bookingService.holdSeats(USER, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("10");
    }


    @Test
    @DisplayName("Thanh toán đổi ghế sang BOOKED, đơn PAID và ghi giao dịch SUCCESS")
    void payBooksSeatsAndCreatesPayment() {
        BookingResponse held = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));

        BookingResponse paid = bookingService.pay(USER, held.id(),
                new PaymentRequest("VNPAY", "NCB", null, null));

        assertThat(paid.status()).isEqualTo(BookingStatus.PAID.name());
        assertThat(paid.payment()).isNotNull();
        assertThat(paid.payment().status()).isEqualTo(PaymentStatus.SUCCESS.name());

        Booking booking = bookingRepository.findById(held.id()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAID);
        assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(held.id())).hasSize(1);

        TripSeat seat = tripSeatRepository.findById(held.seats().get(0).seatId()).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getHoldingUntil()).isNull();
    }

    @Test
    @DisplayName("Thanh toán lại đơn đã PAID không tạo giao dịch thứ hai (idempotent)")
    void payIsIdempotent() {
        BookingResponse held = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));
        bookingService.pay(USER, held.id(), new PaymentRequest("VNPAY", null, null, null));

        BookingResponse again = bookingService.pay(USER, held.id(),
                new PaymentRequest("VNPAY", null, null, null));

        assertThat(again.status()).isEqualTo(BookingStatus.PAID.name());
        assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(held.id())).hasSize(1);
    }

    @Test
    @DisplayName("Thanh toán đơn quá hạn thì 409")
    void cannotPayExpiredBooking() {
        BookingResponse held = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));
        Booking booking = bookingRepository.findById(held.id()).orElseThrow();
        booking.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        bookingRepository.saveAndFlush(booking);

        assertThatThrownBy(() -> bookingService.pay(USER, held.id(),
                new PaymentRequest("VNPAY", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("hết hạn");
    }

    @Test
    @DisplayName("Cổng báo mã lỗi thì ghi giao dịch FAILED và đơn giữ PENDING")
    void failedGatewayResponseKeepsBookingPending() {
        BookingResponse held = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));

        assertThatThrownBy(() -> bookingService.pay(USER, held.id(),
                new PaymentRequest("VNPAY", "NCB", null, "24")))
                .isInstanceOf(ConflictException.class);

        assertThat(bookingRepository.findById(held.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
        assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(held.id()))
                .singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED));
    }


    @Test
    @DisplayName("Huỷ đơn PENDING trả ghế về AVAILABLE")
    void cancelReleasesSeats() {
        BookingResponse held = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));

        BookingResponse cancelled = bookingService.cancel(USER, held.id());

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED.name());
        TripSeat seat = tripSeatRepository.findById(held.seats().get(0).seatId()).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHoldingUntil()).isNull();
    }

    @Test
    @DisplayName("Không huỷ được đơn đã thanh toán")
    void cannotCancelPaidBooking() {
        BookingResponse held = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));
        bookingService.pay(USER, held.id(), new PaymentRequest("VNPAY", null, null, null));

        assertThatThrownBy(() -> bookingService.cancel(USER, held.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("thanh toán");
    }


    @Test
    @DisplayName("Người khác không xem được đơn của mình")
    void otherUserCannotReadBooking() {
        BookingResponse held = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));

        assertThatThrownBy(() -> bookingService.getOne(OTHER_USER, held.id()))
                .isInstanceOf(AccessDeniedException.class);
    }


    @Test
    @DisplayName("Đơn hàng của tôi lọc theo trạng thái")
    void listMineFiltersByStatus() {
        BookingResponse pending = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));
        BookingResponse toCancel = bookingService.holdSeats(USER, holdRequest(seats().get(1).getId()));
        bookingService.cancel(USER, toCancel.id());

        PageResponse<BookingResponse> pendingOnly =
                bookingService.listMine(USER, List.of(BookingStatus.PENDING), 0, 10);
        PageResponse<BookingResponse> all = bookingService.listMine(USER, null, 0, 10);

        assertThat(pendingOnly.content()).extracting(BookingResponse::id).containsExactly(pending.id());
        assertThat(all.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Admin xem được danh sách khách theo chuyến")
    void adminListsBookingsByTrip() {
        bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));
        bookingService.holdSeats(OTHER_USER, holdRequest(seats().get(1).getId()));

        PageResponse<BookingResponse> result =
                bookingService.listForAdmin(trip.getId(), null, 0, 10);

        assertThat(result.totalElements()).isEqualTo(2);
    }


    @Test
    @DisplayName("Job trả ghế quá hạn và đánh dấu đơn EXPIRED")
    void expiryJobReleasesSeatsAndExpiresBookings() {
        BookingResponse held = bookingService.holdSeats(USER, holdRequest(seats().get(0).getId()));

        Booking booking = bookingRepository.findById(held.id()).orElseThrow();
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepository.saveAndFlush(booking);
        TripSeat seat = tripSeatRepository.findById(held.seats().get(0).seatId()).orElseThrow();
        seat.setHoldingUntil(LocalDateTime.now().minusMinutes(1));
        tripSeatRepository.saveAndFlush(seat);

        bookingExpiryJob.releaseExpiredHolds();

        assertThat(bookingRepository.findById(held.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        TripSeat after = tripSeatRepository.findById(held.seats().get(0).seatId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(after.getHoldingUntil()).isNull();
    }


    private List<TripSeat> seats() {
        return tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId());
    }

    private HoldSeatsRequest holdRequest(Integer... seatIds) {
        return new HoldSeatsRequest(trip.getId(), List.of(seatIds), null, null,
                "Nguyễn Văn A", "0901234567", "a@example.com");
    }

    private Trip createOtherTrip() {
        Trip other = new Trip();
        other.setRoute(trip.getRoute());
        other.setCompany(trip.getCompany());
        other.setBusType(BusType.SLEEPER_45);
        other.setDepartureTime(LocalDateTime.of(2026, 10, 6, 6, 0));
        other.setArrivalTime(LocalDateTime.of(2026, 10, 6, 14, 0));
        other.setBasePrice(new BigDecimal("400000"));
        other.setStatus(TripStatus.ACTIVE);
        other = tripRepository.saveAndFlush(other);
        tripSeatRepository.saveAll(seatGenerator.generate(other));
        return other;
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }
}
