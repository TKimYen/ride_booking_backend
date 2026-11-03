package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.dto.PromotionRequest;
import com.ridebooking.booking.dto.PromotionResponse;
import com.ridebooking.booking.dto.PromotionValidateRequest;
import com.ridebooking.booking.dto.PromotionValidateResponse;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Promotion;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.DiscountType;
import com.ridebooking.booking.enums.PaymentGateway;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.BadRequestException;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.repository.BookingDetailRepository;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.PaymentRepository;
import com.ridebooking.booking.repository.PromotionRepository;
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

@SpringBootTest
@Transactional
class PromotionFlowTest {

    private static final int USER_ID = 999;

    @Autowired private PromotionService promotionService;
    @Autowired private BookingService bookingService;

    @Autowired private PromotionRepository promotionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingDetailRepository bookingDetailRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private SeatGenerator seatGenerator;

    private Trip trip;
    private BusCompany company;
    private Route route;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingDetailRepository.deleteAll();
        bookingRepository.deleteAll();
        tripSeatRepository.deleteAll();
        tripRepository.deleteAll();
        promotionRepository.deleteAll();
        routeRepository.deleteAll();
        busCompanyRepository.deleteAll();
        locationRepository.deleteAll();

        Location hn = new Location();
        hn.setName("Hà Nội");
        hn.setSlug("ha-noi");
        hn = locationRepository.save(hn);

        Location sp = new Location();
        sp.setName("Sa Pa");
        sp.setSlug("sa-pa");
        sp = locationRepository.save(sp);

        company = new BusCompany();
        company.setName("Sao Việt");
        company = busCompanyRepository.save(company);

        route = new Route();
        route.setName("Hà Nội - Sa Pa");
        route.setOrigin(hn);
        route.setDestination(sp);
        route = routeRepository.save(route);

        trip = new Trip();
        trip.setRoute(route);
        trip.setCompany(company);
        trip.setBusType(BusType.SLEEPER_45);
        trip.setDepartureTime(LocalDateTime.now().plusDays(2).withHour(8).withMinute(0));
        trip.setArrivalTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0));
        trip.setBasePrice(new BigDecimal("200000.00"));
        trip.setStatus(TripStatus.ACTIVE);
        trip = tripRepository.saveAndFlush(trip);

        tripSeatRepository.saveAllAndFlush(seatGenerator.generate(trip));
    }

    @Test
    @DisplayName("Lấy danh sách mã khuyến mãi: tất cả, featured, và theo tripId")
    void listActivePromotions_allScenarios() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Mã toàn hệ thống, active, featured
        createPromo("HE2026", "Hè 2026", DiscountType.PERCENT, new BigDecimal("10"),
                new BigDecimal("50000"), BigDecimal.ZERO, null, null,
                now.minusDays(1), now.plusDays(30), null, true, true);

        // 2. Mã chỉ cho nhà xe khác
        BusCompany otherCompany = new BusCompany();
        otherCompany.setName("Nhà xe khác");
        otherCompany = busCompanyRepository.save(otherCompany);

        createPromo("KHACXE", "Khác xe", DiscountType.FIXED, new BigDecimal("30000"),
                null, BigDecimal.ZERO, otherCompany, null,
                now.minusDays(1), now.plusDays(30), null, false, true);

        // 3. Mã đã hết hạn
        createPromo("HETHAN", "Hết hạn", DiscountType.PERCENT, new BigDecimal("20"),
                null, BigDecimal.ZERO, null, null,
                now.minusDays(10), now.minusDays(1), null, false, true);

        // Test lấy tất cả mã active còn hạn
        List<PromotionResponse> allActive = promotionService.listActivePromotions(null, null);
        assertThat(allActive).extracting(PromotionResponse::code).containsExactlyInAnyOrder("HE2026", "KHACXE");

        // Test lấy featured
        List<PromotionResponse> featured = promotionService.listActivePromotions(true, null);
        assertThat(featured).extracting(PromotionResponse::code).containsExactly("HE2026");

        // Test lấy theo tripId (áp dụng cho trip: mã toàn hệ thống hoặc đúng nhà xe)
        List<PromotionResponse> forTrip = promotionService.listActivePromotions(null, trip.getId());
        assertThat(forTrip).extracting(PromotionResponse::code).containsExactly("HE2026");
    }

    @Test
    @DisplayName("Validate mã khuyến mãi: tính tiền giảm chính xác (PERCENT & maxDiscount)")
    void validatePromotion_percentWithMaxDiscount() {
        LocalDateTime now = LocalDateTime.now();
        createPromo("GIAM10", "Giảm 10%", DiscountType.PERCENT, new BigDecimal("10"),
                new BigDecimal("30000"), new BigDecimal("100000"), null, null,
                now.minusDays(1), now.plusDays(30), null, false, true);

        List<TripSeat> seats = seats();
        // Chọn 2 ghế = 2 * 200,000 = 400,000đ. 10% = 40,000đ nhưng trần maxDiscount = 30,000đ
        PromotionValidateRequest request = new PromotionValidateRequest(
                trip.getId(),
                List.of(seats.get(0).getId(), seats.get(1).getId()),
                "GIAM10"
        );

        PromotionValidateResponse response = promotionService.validatePromotion(request);
        assertThat(response.code()).isEqualTo("GIAM10");
        assertThat(response.subtotal()).isEqualByComparingTo("400000.00");
        assertThat(response.discount()).isEqualByComparingTo("30000.00");
        assertThat(response.total()).isEqualByComparingTo("370000.00");
    }

    @Test
    @DisplayName("Validate mã khuyến mãi: báo lỗi khi đơn chưa đạt giá trị tối thiểu")
    void validatePromotion_minOrderValueNotMet_throwsException() {
        LocalDateTime now = LocalDateTime.now();
        createPromo("MIN500", "Đơn tối thiểu 500k", DiscountType.FIXED, new BigDecimal("50000"),
                null, new BigDecimal("500000"), null, null,
                now.minusDays(1), now.plusDays(30), null, false, true);

        List<TripSeat> seats = seats();
        // Chọn 1 ghế = 200,000đ < 500,000đ
        PromotionValidateRequest request = new PromotionValidateRequest(
                trip.getId(),
                List.of(seats.get(0).getId()),
                "MIN500"
        );

        assertThatThrownBy(() -> promotionService.validatePromotion(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đơn tối thiểu 500000");
    }

    @Test
    @DisplayName("Đặt vé có mã khuyến mãi: giảm giá ngay khi giữ ghế, chỉ tăng used_count khi PAID")
    void bookingWithPromotion_usedCountIncrementsOnlyOnPaid() {
        LocalDateTime now = LocalDateTime.now();
        Promotion promo = createPromo("SALE50", "Giảm 50k", DiscountType.FIXED, new BigDecimal("50000"),
                null, BigDecimal.ZERO, null, null,
                now.minusDays(1), now.plusDays(30), 10, false, true);

        List<TripSeat> seats = seats();
        HoldSeatsRequest request = new HoldSeatsRequest(
                trip.getId(),
                List.of(seats.get(0).getId()),
                null, null,
                "Nguyễn Văn A", "0901234567", "a@example.com",
                "SALE50"
        );

        // 1. Giữ ghế PENDING
        BookingResponse booking = bookingService.holdSeats(USER_ID, request);
        assertThat(booking.subtotalPrice()).isEqualByComparingTo("200000.00");
        assertThat(booking.discountAmount()).isEqualByComparingTo("50000.00");
        assertThat(booking.totalPrice()).isEqualByComparingTo("150000.00");
        assertThat(booking.promotionCode()).isEqualTo("SALE50");
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING.name());

        // Kiểm tra used_count vẫn là 0 khi PENDING
        Promotion promoAfterHold = promotionRepository.findById(promo.getId()).orElseThrow();
        assertThat(promoAfterHold.getUsedCount()).isEqualTo(0);

        // 2. Thanh toán đơn PAID
        PaymentRequest paymentRequest = new PaymentRequest(
                PaymentGateway.VNPAY.name(),
                "TXN_PROMO_01",
                "NCB",
                "00"
        );
        BookingResponse paidBooking = bookingService.pay(USER_ID, booking.id(), paymentRequest);
        assertThat(paidBooking.status()).isEqualTo(BookingStatus.PAID.name());

        // Kiểm tra used_count đã tăng lên 1
        Promotion promoAfterPay = promotionRepository.findById(promo.getId()).orElseThrow();
        assertThat(promoAfterPay.getUsedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Admin CRUD mã khuyến mãi: thêm, sửa, xoá mã chưa dùng thành công; không xoá được mã đã dùng")
    void adminCrudPromotion() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Tạo mới
        PromotionRequest createReq = new PromotionRequest(
                "ADMIN01", "Ưu đãi Admin", "Mô tả", "https://img.com/a.jpg",
                DiscountType.PERCENT, new BigDecimal("15"), new BigDecimal("100000"),
                BigDecimal.ZERO, null, null,
                now.plusDays(1), now.plusDays(10), 50, true, true
        );
        PromotionResponse created = promotionService.createPromotion(createReq);
        assertThat(created.id()).isNotNull();
        assertThat(created.code()).isEqualTo("ADMIN01");

        // 2. Trùng mã -> lỗi 409 Conflict
        assertThatThrownBy(() -> promotionService.createPromotion(createReq))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Mã 'ADMIN01' đã tồn tại");

        // 3. Sửa mã
        PromotionRequest updateReq = new PromotionRequest(
                "ADMIN01_EDIT", "Ưu đãi sửa", "Mô tả sửa", null,
                DiscountType.FIXED, new BigDecimal("25000"), null,
                BigDecimal.ZERO, null, null,
                now.plusDays(1), now.plusDays(20), 100, false, true
        );
        PromotionResponse updated = promotionService.updatePromotion(created.id(), updateReq);
        assertThat(updated.code()).isEqualTo("ADMIN01_EDIT");
        assertThat(updated.discountValue()).isEqualByComparingTo("25000.00");

        // 4. Xoá mã khi chưa dùng -> thành công
        promotionService.deletePromotion(created.id());
        assertThat(promotionRepository.findById(created.id())).isEmpty();

        // 5. Thử xoá mã đã dùng -> ném ConflictException (HTTP 409)
        Promotion usedPromo = createPromo("DA_DUNG", "Đã dùng", DiscountType.FIXED, new BigDecimal("10000"),
                null, BigDecimal.ZERO, null, null,
                now.minusDays(1), now.plusDays(10), null, false, true);
        usedPromo.setUsedCount(3);
        promotionRepository.saveAndFlush(usedPromo);

        assertThatThrownBy(() -> promotionService.deletePromotion(usedPromo.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Mã đã được dùng, hãy tắt thay vì xoá");
    }

    private List<TripSeat> seats() {
        return tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId());
    }

    private Promotion createPromo(String code, String title, DiscountType discountType,
                                  BigDecimal discountValue, BigDecimal maxDiscount, BigDecimal minOrderValue,
                                  BusCompany company, Route route, LocalDateTime startsAt, LocalDateTime endsAt,
                                  Integer usageLimit, boolean featured, boolean active) {
        Promotion p = new Promotion();
        p.setCode(code);
        p.setTitle(title);
        p.setDiscountType(discountType);
        p.setDiscountValue(discountValue);
        p.setMaxDiscount(maxDiscount);
        p.setMinOrderValue(minOrderValue != null ? minOrderValue : BigDecimal.ZERO);
        p.setCompany(company);
        p.setRoute(route);
        p.setStartsAt(startsAt);
        p.setEndsAt(endsAt);
        p.setUsageLimit(usageLimit);
        p.setUsedCount(0);
        p.setFeatured(featured);
        p.setActive(active);
        return promotionRepository.saveAndFlush(p);
    }
}
