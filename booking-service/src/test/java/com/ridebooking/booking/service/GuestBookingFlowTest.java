package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridebooking.booking.dto.BookingLookupRequest;
import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.BadRequestException;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BookingDetailRepository;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.PaymentRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import com.ridebooking.booking.util.GuestTokens;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khách vãng lai đặt vé (db.md B8): tạo đơn không JWT, quyền bằng token, tra cứu
 * bằng mã vé + SĐT, gắn vé vào tài khoản. Chạy thật trên H2 như {@link BookingFlowTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestBookingFlowTest {

    private static final int USER = 101;
    private static final int OTHER_USER = 202;
    private static final String PHONE = "0901234567";

    @Autowired private BookingService bookingService;
    @Autowired private MockMvc mockMvc;

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
        trip.setDepartureTime(LocalDateTime.now().plusDays(3));
        trip.setArrivalTime(LocalDateTime.now().plusDays(3).plusHours(8));
        trip.setBasePrice(new BigDecimal("500000"));
        trip.setStatus(TripStatus.ACTIVE);
        trip = tripRepository.saveAndFlush(trip);
        tripSeatRepository.saveAll(seatGenerator.generate(trip));
    }


    @Test
    @DisplayName("Khách vãng lai giữ ghế: đơn không có userId, có mã vé, DB chỉ lưu hash của token")
    void guestHoldCreatesGuestBooking() {
        BookingResponse response = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));

        assertThat(response.guest()).isTrue();
        assertThat(response.userId()).isNull();
        assertThat(response.bookingCode()).hasSize(8);
        assertThat(response.guestToken()).isNotBlank();

        Booking saved = bookingRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getGuestTokenHash()).isNotEqualTo(response.guestToken())
                .isEqualTo(GuestTokens.hash(response.guestToken()));
    }

    @Test
    @DisplayName("Khách vãng lai bắt buộc nhập email")
    void guestHoldRequiresEmail() {
        assertThatThrownBy(() -> bookingService.holdSeatsAsGuest(holdRequest(null, seatId(0))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Token đúng thì xem, thanh toán được; token sai hoặc không có token bị từ chối")
    void guestTokenGrantsAccess() {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));
        String token = held.guestToken();

        assertThat(bookingService.getOne(null, token, held.id()).id()).isEqualTo(held.id());
        assertThatThrownBy(() -> bookingService.getOne(null, "sai-token", held.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bookingService.getOne(null, null, held.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bookingService.getOne(USER, null, held.id()))
                .isInstanceOf(AccessDeniedException.class);

        BookingResponse paid = bookingService.pay(null, token, held.id(), new PaymentRequest("VNPAY", null, null, null));
        assertThat(paid.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Khách vãng lai huỷ được đơn của mình bằng token")
    void guestCanCancelWithToken() {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));

        BookingResponse cancelled = bookingService.cancel(null, held.guestToken(), held.id());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Token khách không mở được đơn của tài khoản")
    void guestTokenCannotOpenAccountBooking() {
        BookingResponse guest = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));
        BookingResponse member = bookingService.holdSeats(USER, holdRequest("b@example.com", seatId(1)));

        assertThatThrownBy(() -> bookingService.getOne(null, guest.guestToken(), member.id()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Tra cứu bằng mã vé + SĐT cấp token mới, token cũ hết hiệu lực")
    void lookupRotatesGuestToken() {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));

        BookingResponse found = bookingService.lookup(
                new BookingLookupRequest(held.bookingCode().toLowerCase(), PHONE));

        assertThat(found.id()).isEqualTo(held.id());
        assertThat(found.guestToken()).isNotBlank().isNotEqualTo(held.guestToken());
        assertThat(bookingService.getOne(null, found.guestToken(), held.id()).id()).isEqualTo(held.id());
        assertThatThrownBy(() -> bookingService.getOne(null, held.guestToken(), held.id()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Tra cứu sai SĐT trả 404 chung, không lộ mã có tồn tại")
    void lookupWithWrongPhoneIsNotFound() {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));

        assertThatThrownBy(() -> bookingService.lookup(new BookingLookupRequest(held.bookingCode(), "0999999999")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy vé với mã và số điện thoại này");
    }

    @Test
    @DisplayName("Tra cứu đơn của tài khoản chỉ trả thông tin, không cấp token")
    void lookupAccountBookingHasNoToken() {
        BookingResponse member = bookingService.holdSeats(USER, holdRequest("b@example.com", seatId(0)));

        BookingResponse found = bookingService.lookup(new BookingLookupRequest(member.bookingCode(), PHONE));

        assertThat(found.guestToken()).isNull();
        assertThat(bookingRepository.findById(member.id()).orElseThrow().getGuestTokenHash()).isNull();
    }

    @Test
    @DisplayName("Gắn vé vào tài khoản: có userId, xoá token khách, hiện ở Đơn hàng của tôi")
    void claimMovesGuestBookingToAccount() {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));

        BookingResponse claimed = bookingService.claim(USER, new BookingLookupRequest(held.bookingCode(), PHONE));

        assertThat(claimed.userId()).isEqualTo(USER);
        assertThat(claimed.guest()).isFalse();
        Booking saved = bookingRepository.findById(held.id()).orElseThrow();
        assertThat(saved.getGuestTokenHash()).isNull();
        assertThat(bookingService.listMine(USER, List.of(BookingStatus.PENDING), 0, 10).content())
                .extracting(BookingResponse::id).containsExactly(held.id());
        assertThatThrownBy(() -> bookingService.getOne(null, held.guestToken(), held.id()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Vé đã thuộc tài khoản khác thì không gắn được")
    void claimRejectsBookingOfAnotherAccount() {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));
        bookingService.claim(USER, new BookingLookupRequest(held.bookingCode(), PHONE));

        assertThatThrownBy(() -> bookingService.claim(OTHER_USER, new BookingLookupRequest(held.bookingCode(), PHONE)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("API nội bộ cho payment-service báo guestAccess theo token chuyển tiếp")
    void internalViewReportsGuestAccess() {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));

        assertThat(bookingService.getForPayment(held.id(), held.guestToken()).guestAccess()).isTrue();
        assertThat(bookingService.getForPayment(held.id(), "sai").guestAccess()).isFalse();
        assertThat(bookingService.getForPayment(held.id(), null).guestAccess()).isFalse();
    }


    // ---------------------------------------------------------------- tầng HTTP

    @Test
    @DisplayName("HTTP: POST /api/bookings không JWT -> 201 kèm mã vé và guestToken")
    void httpGuestCanCreateBooking() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tripId": %d, "seatIds": [%d], "contactName": "Khách",
                                 "contactPhone": "%s", "contactEmail": "a@example.com"}
                                """.formatted(trip.getId(), seatId(0), PHONE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guest").value(true))
                .andExpect(jsonPath("$.bookingCode").isNotEmpty())
                .andExpect(jsonPath("$.guestToken").isNotEmpty());
    }

    @Test
    @DisplayName("HTTP: xem đơn không JWT, không token -> 401; có token đúng -> 200 và không lộ token")
    void httpGuestTokenHeader() throws Exception {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));

        mockMvc.perform(get("/api/bookings/{id}", held.id()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/{id}", held.id()).header("X-Guest-Token", held.guestToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(held.id()))
                .andExpect(jsonPath("$.guestToken").doesNotExist());
        mockMvc.perform(get("/api/bookings/{id}", held.id()).header("X-Guest-Token", "sai"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HTTP: Đơn của tôi và gắn vé vẫn cần JWT")
    void httpAccountOnlyEndpointsNeedJwt() throws Exception {
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingCode\":\"ABCDEFGH\",\"phone\":\"0901234567\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HTTP: tra cứu vé công khai")
    void httpLookupIsPublic() throws Exception {
        BookingResponse held = bookingService.holdSeatsAsGuest(holdRequest("a@example.com", seatId(0)));

        mockMvc.perform(post("/api/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingCode\":\"%s\",\"phone\":\"%s\"}".formatted(held.bookingCode(), PHONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestToken").isNotEmpty());
    }


    private Integer seatId(int index) {
        List<TripSeat> seats = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId());
        return seats.get(index).getId();
    }

    private HoldSeatsRequest holdRequest(String email, Integer... seatIds) {
        return new HoldSeatsRequest(trip.getId(), List.of(seatIds), null, null, "Nguyễn Văn A", PHONE, email);
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }
}
