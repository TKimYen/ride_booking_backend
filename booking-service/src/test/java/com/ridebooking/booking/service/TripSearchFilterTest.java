package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.dto.ReviewRequest;
import com.ridebooking.booking.dto.StopPointOptionsResponse;
import com.ridebooking.booking.dto.TripSearchItemResponse;
import com.ridebooking.booking.dto.TripSearchRequest;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.StopPoint;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.PointType;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.StopPointRepository;
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

/** Bộ lọc tìm kiếm [4.4]: nhiều nhà xe, loại xe, điểm đón/trả, kèm điểm nhà xe. */
@SpringBootTest
@Transactional
class TripSearchFilterTest {

    @Autowired private TripService tripService;
    @Autowired private StopPointService stopPointService;
    @Autowired private ReviewService reviewService;
    @Autowired private BookingService bookingService;
    @Autowired private TripLifecycleService tripLifecycleService;
    @Autowired private SeatGenerator seatGenerator;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private StopPointRepository stopPointRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private LocationRepository locationRepository;

    private Location origin;
    private Location destination;
    private Route route;
    private BusCompany companyA;
    private BusCompany companyB;
    private BusCompany companyC;
    private Trip limoA;
    private Trip sleeperB;
    private Trip cabinC;

    @BeforeEach
    void setUp() {
        origin = locationRepository.save(location("Điểm đi lọc", "diem-di-loc"));
        destination = locationRepository.save(location("Điểm đến lọc", "diem-den-loc"));
        Route r = new Route();
        r.setName("Tuyến lọc");
        r.setOrigin(origin);
        r.setDestination(destination);
        route = routeRepository.save(r);

        companyA = company("Nhà xe lọc A");
        companyB = company("Nhà xe lọc B");
        companyC = company("Nhà xe lọc C");

        limoA = trip(companyA, BusType.LIMOUSINE_29, 1);
        sleeperB = trip(companyB, BusType.SLEEPER_45, 2);
        cabinC = trip(companyC, BusType.CABIN_DOUBLE_22, 3);

        stop(limoA, PointType.PICKUP, "Bến xe Mỹ Đình", 1);
        stop(limoA, PointType.PICKUP, "Ngã tư Sở", 2);
        stop(limoA, PointType.DROPOFF, "Bến xe Sa Pa", 1);
        stop(sleeperB, PointType.PICKUP, "Bến xe Giáp Bát", 1);
        stop(sleeperB, PointType.DROPOFF, "Quảng trường Sa Pa", 1);
        stop(cabinC, PointType.PICKUP, "Ngã tư Sở", 1);
        stop(cabinC, PointType.DROPOFF, "Bến xe Sa Pa", 1);
    }

    @Test
    @DisplayName("Chọn nhiều nhà xe cùng lúc")
    void filterByManyCompanies() {
        assertThat(ids(search(List.of(companyA.getId(), companyB.getId()), null, null, null)))
                .containsExactly(limoA.getId(), sleeperB.getId());
    }

    @Test
    @DisplayName("Lọc theo loại xe")
    void filterByBusType() {
        assertThat(ids(search(null, List.of(BusType.SLEEPER_45, BusType.CABIN_DOUBLE_22), null, null)))
                .containsExactly(sleeperB.getId(), cabinC.getId());
    }

    @Test
    @DisplayName("Lọc theo điểm đón và điểm trả; chuyến nhiều điểm khớp không bị lặp")
    void filterByStopPoints() {
        assertThat(ids(search(null, null, List.of("Ngã tư Sở", "Bến xe Mỹ Đình"), null)))
                .containsExactly(limoA.getId(), cabinC.getId());
        assertThat(ids(search(null, null, List.of("Ngã tư Sở"), List.of("Bến xe Sa Pa"))))
                .containsExactly(limoA.getId(), cabinC.getId());
        assertThat(ids(search(null, null, List.of("Bến xe Giáp Bát"), List.of("Bến xe Sa Pa")))).isEmpty();
    }

    @Test
    @DisplayName("Kết quả kèm điểm nhà xe và điểm đón đầu / trả cuối")
    void resultCarriesRatingAndPreview() {
        reviewOn(companyA, 5);

        List<TripSearchItemResponse> items = search(null, null, null, null);
        TripSearchItemResponse a = items.stream().filter(i -> i.trip().id().equals(limoA.getId())).findFirst().orElseThrow();
        TripSearchItemResponse b = items.stream().filter(i -> i.trip().id().equals(sleeperB.getId())).findFirst().orElseThrow();

        assertThat(a.rating()).isEqualByComparingTo("5.0");
        assertThat(a.ratingCount()).isEqualTo(1);
        assertThat(a.pickupPreview()).isEqualTo("Bến xe Mỹ Đình");
        assertThat(a.dropoffPreview()).isEqualTo("Bến xe Sa Pa");
        assertThat(b.rating()).isNull();
        assertThat(b.ratingCount()).isZero();

        assertThat(tripService.getItemById(limoA.getId()).rating()).isEqualByComparingTo("5.0");
    }

    @Test
    @DisplayName("Không trả chuyến đã khởi hành dù vẫn ACTIVE (chưa tới giờ đến)")
    void excludesDepartedTrips() {
        Trip departed = trip(companyA, BusType.LIMOUSINE_29, 1);
        departed.setDepartureTime(LocalDateTime.now().minusHours(1));
        departed.setArrivalTime(LocalDateTime.now().plusHours(5));
        tripRepository.saveAndFlush(departed);

        assertThat(ids(search(null, null, null, null)))
                .containsExactly(limoA.getId(), sleeperB.getId(), cabinC.getId());

        // Lọc theo ngày hôm nay cũng không ra chuyến đã chạy
        TripSearchRequest today = new TripSearchRequest(origin.getId(), destination.getId(),
                null, java.time.LocalDate.now(), null, null, null, null, "departureAsc", null, null, null, null);
        assertThat(ids(tripService.search(today, 0, 50).content())).doesNotContain(departed.getId());
    }

    @Test
    @DisplayName("Danh sách tên điểm đón/trả để dựng bộ lọc")
    void stopPointOptions() {
        StopPointOptionsResponse options = stopPointService.options(origin.getId(), destination.getId(), null);

        assertThat(options.pickups()).containsExactly("Bến xe Giáp Bát", "Bến xe Mỹ Đình", "Ngã tư Sở");
        assertThat(options.dropoffs()).containsExactly("Bến xe Sa Pa", "Quảng trường Sa Pa");
    }

    // ------------------------------------------------------------ helpers

    private List<TripSearchItemResponse> search(List<Integer> companyIds, List<BusType> busTypes,
                                                List<String> pickups, List<String> dropoffs) {
        TripSearchRequest request = new TripSearchRequest(origin.getId(), destination.getId(), null, null,
                null, null, null, null, "departureAsc", companyIds, busTypes, pickups, dropoffs);
        return tripService.search(request, 0, 50).content();
    }

    private static List<Integer> ids(List<TripSearchItemResponse> items) {
        return items.stream().map(i -> i.trip().id()).toList();
    }

    /** Một đánh giá {@code stars} sao cho nhà xe, trên một chuyến riêng đã chạy xong. */
    private void reviewOn(BusCompany company, int stars) {
        Trip done = trip(company, BusType.LIMOUSINE_29, 10);
        Integer seatId = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(done.getId()).getFirst().getId();
        Integer booking = bookingService.holdSeats(301, new HoldSeatsRequest(done.getId(), List.of(seatId), null, null,
                "Khách lọc", "0901234567", "loc@example.com")).id();
        bookingService.pay(301, booking, new PaymentRequest("VNPAY", null, null, null));
        done.setDepartureTime(LocalDateTime.now().minusHours(10));
        done.setArrivalTime(LocalDateTime.now().minusHours(2));
        tripRepository.saveAndFlush(done);
        tripLifecycleService.completeArrivedTrips(LocalDateTime.now());
        reviewService.create(301, new ReviewRequest(booking, stars, null));
    }

    private Trip trip(BusCompany company, BusType busType, int daysAhead) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setCompany(company);
        trip.setBusType(busType);
        trip.setDepartureTime(LocalDateTime.now().plusDays(daysAhead));
        trip.setArrivalTime(LocalDateTime.now().plusDays(daysAhead).plusHours(8));
        trip.setBasePrice(new BigDecimal("300000"));
        trip.setStatus(TripStatus.ACTIVE);
        trip = tripRepository.saveAndFlush(trip);
        tripSeatRepository.saveAll(seatGenerator.generate(trip));
        return trip;
    }

    private void stop(Trip trip, PointType type, String name, int order) {
        StopPoint point = new StopPoint();
        point.setTrip(trip);
        point.setPointType(type);
        point.setName(name);
        point.setStopOrder(order);
        point.setEstimatedTime(trip.getDepartureTime());
        stopPointRepository.saveAndFlush(point);
    }

    private BusCompany company(String name) {
        BusCompany company = new BusCompany();
        company.setName(name);
        return busCompanyRepository.save(company);
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }
}
