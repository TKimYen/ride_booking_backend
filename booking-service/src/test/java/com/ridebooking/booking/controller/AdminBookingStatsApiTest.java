package com.ridebooking.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.dto.stats.DailyRevenueResponse;
import com.ridebooking.booking.dto.stats.StatsOverviewResponse;
import com.ridebooking.booking.dto.stats.StatusCountResponse;
import com.ridebooking.booking.dto.stats.TopRouteResponse;
import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import com.ridebooking.booking.service.BookingService;
import com.ridebooking.booking.service.SeatGenerator;
import com.ridebooking.booking.service.StatsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý đơn của Admin [4.5] (tìm theo từ khoá/khoảng ngày, huỷ kèm lý do) và
 * API thống kê Dashboard [3.5].
 *
 * <p>Số liệu thống kê được so theo chênh lệch trước/sau, vì cùng DB H2 có thể có
 * dữ liệu của test khác hoặc dữ liệu mẫu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBookingStatsApiTest {

    private static final int USER = 101;

    @Autowired private MockMvc mockMvc;
    @Autowired private BookingService bookingService;
    @Autowired private StatsService statsService;
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
        Location from = locationRepository.save(location("Điểm đi thống kê", "diem-di-thong-ke"));
        Location to = locationRepository.save(location("Điểm đến thống kê", "diem-den-thong-ke"));
        BusCompany newCompany = new BusCompany();
        newCompany.setName("Nhà xe thống kê");
        company = busCompanyRepository.save(newCompany);
        Route newRoute = new Route();
        newRoute.setName("Tuyến thống kê");
        newRoute.setOrigin(from);
        newRoute.setDestination(to);
        route = routeRepository.save(newRoute);
    }

    // ---- [4.5] Tìm đơn ----

    @Test
    @DisplayName("Tìm theo từ khoá: khớp một phần mã vé, tên, SĐT, email, không phân biệt hoa thường")
    void searchByKeyword() {
        Trip trip = futureTrip();
        BookingResponse an = hold(trip, 0, "Trần Thị Bích", "0911222333", "bich@example.com");
        BookingResponse binh = hold(trip, 1, "Lê Văn Bình Thống Kê", "0988777666", "binh.thongke@example.com");

        assertThat(idsOf(search("0911222", null, null))).containsExactly(an.id());
        assertThat(idsOf(search("BÌNH THỐNG", null, null))).containsExactly(binh.id());
        assertThat(idsOf(search("BINH.THONGKE@", null, null))).containsExactly(binh.id());
        assertThat(idsOf(search(an.bookingCode().toLowerCase(), null, null))).containsExactly(an.id());
        // Ký tự đại diện của LIKE được escape, không khớp mọi dòng.
        assertThat(idsOf(search("%", null, null))).isEmpty();
    }

    @Test
    @DisplayName("Lọc theo khoảng ngày tạo đơn, tính trọn ngày ở cả hai đầu")
    void searchByDateRange() {
        Trip trip = futureTrip();
        Integer old = hold(trip, 0, "Khách cũ thống kê", "0900000001", null).id();
        Integer recent = hold(trip, 1, "Khách mới thống kê", "0900000002", null).id();
        setCreatedAt(old, LocalDate.of(2026, 1, 10).atTime(23, 59));
        setCreatedAt(recent, LocalDate.of(2026, 1, 12).atTime(0, 0));

        assertThat(idsOf(search("thống kê", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10))))
                .containsExactly(old);
        assertThat(idsOf(search("thống kê", LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 12))))
                .containsExactly(recent);
        assertThat(idsOf(search("thống kê", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))))
                .containsExactly(recent, old);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/bookings: from sau to → 400")
    void searchRejectsInvertedRange() throws Exception {
        mockMvc.perform(get("/api/admin/bookings").param("from", "2026-11-10").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/bookings?keyword= qua HTTP trả đúng đơn")
    void searchOverHttp() throws Exception {
        Trip trip = futureTrip();
        BookingResponse booking = hold(trip, 0, "Khách http thống kê", "0933444555", null);

        mockMvc.perform(get("/api/admin/bookings").param("keyword", "0933444"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingCode").value(booking.bookingCode()));
    }

    // ---- [4.5] Admin huỷ đơn ----

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Huỷ đơn thiếu hoặc trống lý do → 400, đơn giữ nguyên")
    void cancelRequiresReason() throws Exception {
        Trip trip = futureTrip();
        Integer id = hold(trip, 0, "Khách", "0901234567", null).id();

        mockMvc.perform(post("/api/admin/bookings/" + id + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/bookings/" + id + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(bookingStatus(id)).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Huỷ đơn PAID: trả ghế, lưu lý do, trả thông tin cần hoàn tiền")
    void cancelPaidBookingReturnsRefund() throws Exception {
        Trip trip = futureTrip();
        Integer id = holdAndPay(trip, 0);
        double paid = totalOf(id).doubleValue();

        mockMvc.perform(post("/api/admin/bookings/" + id + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Khách gọi tổng đài xin huỷ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CANCELLED"))
                .andExpect(jsonPath("$.booking.cancelReason").value("Khách gọi tổng đài xin huỷ"))
                .andExpect(jsonPath("$.refund.bookingId").value(id))
                .andExpect(jsonPath("$.refund.amount").value(paid))
                .andExpect(jsonPath("$.refund.gateway").value("VNPAY"));

        assertThat(seatsOf(trip).get(0).getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        Booking saved = bookingRepository.findById(id).orElseThrow();
        assertThat(saved.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("Huỷ đơn PENDING: trả ghế, không có hoàn tiền; gọi lại không lỗi")
    void cancelPendingBookingIsIdempotent() {
        Trip trip = futureTrip();
        Integer id = hold(trip, 0, "Khách", "0901234567", null).id();

        var first = bookingService.cancelByAdmin(id, "  Trùng đơn  ");
        var second = bookingService.cancelByAdmin(id, "Lý do khác");

        assertThat(first.booking().status()).isEqualTo("CANCELLED");
        assertThat(first.booking().cancelReason()).isEqualTo("Trùng đơn");
        assertThat(first.refund()).isNull();
        // Lần gọi lại không ghi đè lý do ban đầu.
        assertThat(second.booking().cancelReason()).isEqualTo("Trùng đơn");
        assertThat(seatsOf(trip).get(0).getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Không huỷ được đơn COMPLETED hoặc đơn của chuyến đã khởi hành → 409")
    void cancelRejectsCompletedOrDeparted() throws Exception {
        Trip trip = futureTrip();
        Integer completed = holdAndPay(trip, 0);
        Integer departed = holdAndPay(trip, 1);
        Booking done = bookingRepository.findById(completed).orElseThrow();
        done.setStatus(BookingStatus.COMPLETED);
        bookingRepository.saveAndFlush(done);

        mockMvc.perform(post("/api/admin/bookings/" + completed + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thử\"}"))
                .andExpect(status().isConflict());

        trip.setDepartureTime(LocalDateTime.now().minusHours(1));
        tripRepository.saveAndFlush(trip);
        mockMvc.perform(post("/api/admin/bookings/" + departed + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thử\"}"))
                .andExpect(status().isConflict());
        assertThat(bookingStatus(departed)).isEqualTo(BookingStatus.PAID);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Huỷ đơn không tồn tại → 404")
    void cancelUnknownBooking() throws Exception {
        mockMvc.perform(post("/api/admin/bookings/999999/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thử\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/bookings/{id}: Admin xem được đơn của người khác")
    void adminGetsAnyBooking() throws Exception {
        Trip trip = futureTrip();
        Integer id = hold(trip, 0, "Khách", "0901234567", null).id();

        mockMvc.perform(get("/api/admin/bookings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.cancelReason", nullValue()));
    }

    // ---- [3.5] Thống kê ----

    @Test
    @DisplayName("Tổng quan: doanh thu/vé chỉ tính đơn đã thanh toán; đơn huỷ sau khi trả tiền bị trừ ra")
    void overviewCountsPaidBookings() {
        LocalDate today = LocalDate.now();
        StatsOverviewResponse before = statsService.overview(today, today, null);

        Trip trip = futureTrip();
        Integer paid = holdAndPay(trip, 0, 1); // 2 ghế
        hold(trip, 2, "Chưa trả", "0900000003", null); // chưa trả tiền, không vào doanh thu
        Integer refunded = holdAndPay(trip, 3);
        bookingService.cancelByAdmin(refunded, "Khách đổi ý");

        StatsOverviewResponse after = statsService.overview(today, today, null);

        assertThat(after.revenue().subtract(before.revenue())).isEqualByComparingTo(totalOf(paid));
        assertThat(after.paidBookings() - before.paidBookings()).isEqualTo(1);
        assertThat(after.ticketsSold() - before.ticketsSold()).isEqualTo(2);
        assertThat(after.newBookings() - before.newBookings()).isEqualTo(3);
        assertThat(after.cancelledBookings() - before.cancelledBookings()).isEqualTo(1);
        assertThat(after.upcomingTrips() - before.upcomingTrips()).isEqualTo(1);
    }

    @Test
    @DisplayName("Doanh thu theo ngày: đủ mọi ngày trong khoảng, ngày trống bằng 0")
    void dailyRevenueFillsEveryDay() {
        LocalDate today = LocalDate.now();
        List<DailyRevenueResponse> before = statsService.dailyRevenue(today.minusDays(6), today, null);
        Trip trip = futureTrip();
        Integer paid = holdAndPay(trip, 0);

        List<DailyRevenueResponse> after = statsService.dailyRevenue(today.minusDays(6), today, null);

        assertThat(after).hasSize(7);
        assertThat(after.get(0).date()).isEqualTo(today.minusDays(6));
        assertThat(after.get(6).date()).isEqualTo(today);
        assertThat(after.get(6).revenue().subtract(before.get(6).revenue())).isEqualByComparingTo(totalOf(paid));
        assertThat(after.get(6).tickets() - before.get(6).tickets()).isEqualTo(1);
        // Mặc định 30 ngày gần nhất.
        assertThat(statsService.dailyRevenue(null, null, null)).hasSize(30);
    }

    @Test
    @DisplayName("Số đơn theo trạng thái: luôn đủ 5 trạng thái")
    void bookingsByStatusHasAllStatuses() {
        LocalDate today = LocalDate.now();
        Trip trip = futureTrip();
        holdAndPay(trip, 0);

        List<StatusCountResponse> counts = statsService.bookingsByStatus(today, today, null);

        assertThat(counts).extracting(StatusCountResponse::status)
                .containsExactly("PENDING", "PAID", "CANCELLED", "COMPLETED", "EXPIRED");
        assertThat(counts.get(1).count()).isPositive();
    }

    @Test
    @DisplayName("Tuyến bán chạy: có tuyến vừa bán, đúng số đơn, số vé, doanh thu")
    void topRoutesIncludesSoldRoute() {
        LocalDate today = LocalDate.now();
        Trip trip = futureTrip();
        Integer first = holdAndPay(trip, 0, 1);
        Integer second = holdAndPay(trip, 2);

        List<TopRouteResponse> top = statsService.topRoutes(today, today, null, 20);

        TopRouteResponse ours = top.stream()
                .filter(r -> r.routeId().equals(route.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(ours.routeName()).isEqualTo("Tuyến thống kê");
        assertThat(ours.originName()).isEqualTo("Điểm đi thống kê");
        assertThat(ours.bookings()).isEqualTo(2);
        assertThat(ours.tickets()).isEqualTo(3);
        assertThat(ours.revenue()).isEqualByComparingTo(totalOf(first).add(totalOf(second)));

        // Lọc theo nhà xe: đúng nhà xe thì có, nhà xe khác thì không.
        BusCompany other = new BusCompany();
        other.setName("Nhà xe khác thống kê");
        other = busCompanyRepository.save(other);
        assertThat(statsService.topRoutes(today, today, company.getId(), 20))
                .extracting(TopRouteResponse::routeId).containsExactly(route.getId());
        assertThat(statsService.topRoutes(today, today, other.getId(), 20)).isEmpty();
        assertThat(statsService.overview(today, today, other.getId()).paidBookings()).isZero();
        assertThat(statsService.overview(today, today, company.getId()).paidBookings()).isEqualTo(2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("API thống kê qua HTTP: 200; khoảng ngày sai → 400")
    void statsOverHttp() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").exists());
        mockMvc.perform(get("/api/admin/stats/revenue").param("from", "2026-11-01").param("to", "2026-11-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)));
        mockMvc.perform(get("/api/admin/stats/bookings-by-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
        mockMvc.perform(get("/api/admin/stats/top-routes").param("limit", "3")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/stats/overview").param("from", "2026-11-10").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/stats/revenue").param("from", "2024-01-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Người dùng thường không xem được thống kê và không huỷ đơn kiểu Admin → 403")
    void statsAndCancelRequireAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/bookings/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thử\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- Hỗ trợ ----

    private PageResponse<BookingResponse> search(String keyword, LocalDate from, LocalDate to) {
        return bookingService.searchForAdmin(null, null, keyword, from, to, 0, 50);
    }

    private List<Integer> idsOf(PageResponse<BookingResponse> page) {
        return page.content().stream().map(BookingResponse::id).toList();
    }

    private void setCreatedAt(Integer bookingId, LocalDateTime createdAt) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setCreatedAt(createdAt);
        bookingRepository.saveAndFlush(booking);
    }

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

    private BookingResponse hold(Trip trip, int seatIndex, String name, String phone, String email) {
        Integer seatId = seatsOf(trip).get(seatIndex).getId();
        return bookingService.holdSeats(USER, new HoldSeatsRequest(trip.getId(), List.of(seatId), null, null,
                name, phone, email));
    }

    private Integer holdAndPay(Trip trip, int... seatIndexes) {
        List<TripSeat> seats = seatsOf(trip);
        List<Integer> seatIds = java.util.Arrays.stream(seatIndexes).mapToObj(i -> seats.get(i).getId()).toList();
        Integer id = bookingService.holdSeats(USER, new HoldSeatsRequest(trip.getId(), seatIds, null, null,
                "Khách đã trả", "0901234567", "khach@example.com")).id();
        bookingService.pay(USER, id, new PaymentRequest("VNPAY", null, null, null));
        return id;
    }

    private List<TripSeat> seatsOf(Trip trip) {
        return tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId());
    }

    private BigDecimal totalOf(Integer bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getTotalPrice();
    }

    private BookingStatus bookingStatus(Integer id) {
        return bookingRepository.findById(id).orElseThrow().getStatus();
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }
}
