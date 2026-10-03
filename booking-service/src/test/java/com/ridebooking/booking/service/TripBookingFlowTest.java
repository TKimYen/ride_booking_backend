package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.SeatMapResponse;
import com.ridebooking.booking.dto.StopPointRequest;
import com.ridebooking.booking.dto.StopPointResponse;
import com.ridebooking.booking.dto.TripRequest;
import com.ridebooking.booking.dto.TripResponse;
import com.ridebooking.booking.dto.TripSearchItemResponse;
import com.ridebooking.booking.dto.TripSearchRequest;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.PointType;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.StopPointRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chạy thật qua database H2, không mock repository.
 *
 * <p>Các lỗi đã bắt được ở đây là loại mock không thấy: query JPQL sai cú pháp,
 * ràng buộc DB không khớp entity, và số liệu tổng hợp (đếm ghế, điểm trung bình)
 * tính sai mà mọi test trên service vẫn xanh.
 */
@SpringBootTest
@Transactional
class TripBookingFlowTest {

    @Autowired private TripService tripService;
    @Autowired private TripAdminService tripAdminService;
    @Autowired private TripSeatService tripSeatService;
    @Autowired private StopPointService stopPointService;
    @Autowired private SeatGenerator seatGenerator;

    @Autowired private TripRepository tripRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private StopPointRepository stopPointRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private LocationRepository locationRepository;

    private Location haNoi;
    private Location saPa;
    private BusCompany company;
    private Route route;

    @BeforeEach
    void setUp() {
        tripSeatRepository.deleteAll();
        stopPointRepository.deleteAll();
        tripRepository.deleteAll();
        routeRepository.deleteAll();
        busCompanyRepository.deleteAll();
        locationRepository.deleteAll();

        haNoi = locationRepository.save(location("Hà Nội", "ha-noi"));
        saPa = locationRepository.save(location("Sa Pa", "sa-pa"));

        BusCompany newCompany = new BusCompany();
        newCompany.setName("Nhà xe test");
        company = busCompanyRepository.save(newCompany);

        route = new Route();
        route.setName("Hà Nội - Sa Pa");
        route.setOrigin(haNoi);
        route.setDestination(saPa);
        route.setDistanceKm(new BigDecimal("465"));
        route.setDurationMinutes(510);
        route = routeRepository.save(route);
    }


    @Test
    @DisplayName("Tìm theo tuyến chỉ trả về chuyến đúng tuyến")
    void searchFiltersByRoute() {
        createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        createTrip(LocalDateTime.of(2026, 10, 6, 6, 0), BusType.SLEEPER_45);

        Route otherRoute = new Route();
        otherRoute.setName("Hà Nội - Hải Phòng");
        Location haiPhong = locationRepository.save(location("Hải Phòng", "hai-phong"));
        otherRoute.setOrigin(haNoi);
        otherRoute.setDestination(haiPhong);
        otherRoute.setDistanceKm(new BigDecimal("120"));
        otherRoute.setDurationMinutes(150);
        otherRoute = routeRepository.save(otherRoute);
        createTrip(otherRoute, LocalDateTime.of(2026, 10, 5, 7, 0), BusType.SLEEPER_45);

        PageResponse<TripSearchItemResponse> result =
                tripService.search(new TripSearchRequest(haNoi.getId(), saPa.getId(), null, null,
                        null, null, null, null, null), 0, 10);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).allSatisfy(item ->
                assertThat(item.trip().routeName()).isEqualTo("Hà Nội - Sa Pa"));
    }

    @Test
    @DisplayName("Số ghế trống khớp với dữ liệu ghế, và không phải tổng số ghế")
    void searchReportsAvailableSeats() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        List<TripSeat> seats = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId());
        seats.get(0).setStatus(SeatStatus.BOOKED);
        seats.get(1).setStatus(SeatStatus.HOLDING);
        tripSeatRepository.saveAll(seats);

        TripSearchItemResponse item = tripService.search(
                new TripSearchRequest(null, null, null, null, null, null, null, null, null),
                0, 10).content().get(0);

        assertThat(item.trip().totalSeats()).isEqualTo(BusType.LIMOUSINE_29.seatCount());
        assertThat(item.availableSeats()).isEqualTo(BusType.LIMOUSINE_29.seatCount() - 2);
        // unavailable là số ghế KHÔNG trống. Trước đây chỗ này trả available + taken
        // tức bằng tổng số ghế, nên mọi chuyến đều báo "hết chỗ".
        assertThat(item.unavailableSeats()).isEqualTo(2);
    }

    @Test
    @DisplayName("Chuyến đã huỷ không xuất hiện khi tìm chuyến đang bán")
    void searchHidesCancelledTrips() {
        Trip cancelled = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        cancelled.setStatus(TripStatus.CANCELLED);
        tripRepository.save(cancelled);

        PageResponse<TripSearchItemResponse> result =
                tripService.search(new TripSearchRequest(null, null, null, null,
                        null, null, null, null, null), 0, 10);

        assertThat(result.content()).noneMatch(item -> item.trip().status().equals("CANCELLED"));
    }

    @Test
    @DisplayName("Lọc theo giờ: earliestDeparture không có travelDate thì áp dụng cho hôm nay")
    void timeWindowWithoutDateAppliesToToday() {
        createTrip(LocalDateTime.now().withHour(14).withMinute(0).withSecond(0).withNano(0),
                BusType.LIMOUSINE_29);
        createTrip(LocalDateTime.now().withHour(4).withMinute(0).withSecond(0).withNano(0),
                BusType.SLEEPER_45);

        PageResponse<TripSearchItemResponse> result = tripService.search(
                new TripSearchRequest(null, null, null, null,
                        LocalTime.of(12, 0), null, null, null, null), 0, 10);

        assertThat(result.content()).isNotEmpty().allSatisfy(item ->
                assertThat(item.trip().departureTime().toLocalTime()).isEqualTo(LocalTime.of(14, 0)));
    }

    @Test
    @DisplayName("Sắp xếp giá tăng dần thì chuyến rẻ nhất lên đầu")
    void sortByPriceAscending() {
        Trip expensive = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        expensive.setBasePrice(new BigDecimal("900000"));
        tripRepository.save(expensive);
        Trip cheap = createTrip(LocalDateTime.of(2026, 10, 5, 7, 0), BusType.SLEEPER_45);
        cheap.setBasePrice(new BigDecimal("300000"));
        tripRepository.save(cheap);

        List<TripSearchItemResponse> items = tripService.search(
                new TripSearchRequest(null, null, null, null, null, null, null, null, "priceAsc"),
                0, 10).content();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).trip().basePrice())
                .isLessThan(items.get(1).trip().basePrice());
    }


    @Test
    @DisplayName("Sơ đồ ghế trả về đủ ghế, tính đúng số ghế trống")
    void seatMapReportsSeats() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        TripSeat booked = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()).get(0);
        booked.setStatus(SeatStatus.BOOKED);
        tripSeatRepository.save(booked);

        SeatMapResponse map = tripSeatService.seatMap(trip.getId());

        assertThat(map.totalSeats()).isEqualTo(BusType.LIMOUSINE_29.seatCount());
        assertThat(map.availableSeats()).isEqualTo(BusType.LIMOUSINE_29.seatCount() - 1);
        assertThat(map.floors()).isEqualTo(2);
        assertThat(map.seats()).allSatisfy(seat ->
                assertThat(seat.totalPrice()).isNotNull());
    }

    @Test
    @DisplayName("Ghế giữ chỗ báo thời gian còn lại không âm")
    void holdingSeatNeverReportsNegativeSeconds() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        List<TripSeat> seats = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId());
        TripSeat holding = seats.get(0);
        holding.setStatus(SeatStatus.HOLDING);
        // Hết hạn từ 5 phút trước: job trả ghế chưa chạy tới.
        holding.setHoldingUntil(LocalDateTime.now().minusMinutes(5));
        tripSeatRepository.save(holding);

        SeatMapResponse map = tripSeatService.seatMap(trip.getId());

        assertThat(map.holdSecondsRemaining()).isNotNegative();
    }


    @Test
    @DisplayName("Tạo chuyến sinh kèm đủ ghế ngay trong lần gọi")
    void createTripAlsoCreatesSeats() {
        TripResponse created = tripAdminService.create(tripRequest(BusType.CABIN_DOUBLE_22));

        assertThat(created.totalSeats()).isEqualTo(BusType.CABIN_DOUBLE_22.seatCount());
        assertThat(tripSeatRepository.count()).isEqualTo(BusType.CABIN_DOUBLE_22.seatCount());
    }

    @Test
    @DisplayName("Giờ đến phải sau giờ khởi hành")
    void arrivalMustBeAfterDeparture() {
        TripRequest request = new TripRequest(route.getId(), company.getId(), "LIMOUSINE_29",
                LocalDateTime.of(2026, 10, 5, 10, 0),
                LocalDateTime.of(2026, 10, 5, 8, 0),
                new BigDecimal("250000"), null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tripAdminService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Giờ đến");
    }

    @Test
    @DisplayName("Loại xe lạ thì báo kèm danh sách loại xe hợp lệ")
    void unknownBusTypeListsAllowedValues() {
        TripRequest request = new TripRequest(route.getId(), company.getId(), "BUS_88",
                LocalDateTime.of(2026, 10, 5, 6, 0),
                LocalDateTime.of(2026, 10, 5, 12, 0),
                new BigDecimal("250000"), null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tripAdminService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIMOUSINE_29");
    }

    @Test
    @DisplayName("Không xoá được chuyến đã bán ghế, phải huỷ chuyến")
    void cannotDeleteTripWithSoldSeats() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        TripSeat seat = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()).get(0);
        seat.setStatus(SeatStatus.BOOKED);
        tripSeatRepository.save(seat);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tripAdminService.delete(trip.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("huỷ chuyến");
    }

    @Test
    @DisplayName("Chuyến chưa có khách thì đổi loại xe được, và ghế được sinh lại")
    void canChangeBusTypeBeforeAnyBooking() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);

        tripAdminService.update(trip.getId(), tripRequest(BusType.SLEEPER_45));

        Trip updated = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(updated.getBusType()).isEqualTo(BusType.SLEEPER_45);
        assertThat(tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()))
                .hasSize(BusType.SLEEPER_45.seatCount());
    }

    @Test
    @DisplayName("Không đổi được loại xe khi đang có ghế được giữ")
    void cannotChangeBusTypeWhileSeatsHeld() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        TripSeat seat = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()).get(0);
        seat.setStatus(SeatStatus.HOLDING);
        seat.setHoldingUntil(LocalDateTime.now().plusMinutes(10));
        tripSeatRepository.save(seat);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        tripAdminService.update(trip.getId(), tripRequest(BusType.SLEEPER_45)))
                .isInstanceOf(ConflictException.class);
    }


    @Test
    @DisplayName("Điểm dừng được đánh số tự động theo thứ tự thêm")
    void stopPointsAreNumberedInOrder() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);

        StopPointResponse first = stopPointService.create(trip.getId(),
                new StopPointRequest("Bến Mỹ Đình", "Số 1 Lê Thanh Nghị", "PICKUP", null));
        StopPointResponse second = stopPointService.create(trip.getId(),
                new StopPointRequest("Bến Xã Đàn", null, "PICKUP", null));

        assertThat(first.stopOrder()).isEqualTo(1);
        assertThat(second.stopOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("Xoá điểm giữa thì các điểm còn lại được đánh lại liền mạch từ 1")
    void deletingStopPointRenumbersTheRest() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        StopPointResponse first = stopPointService.create(trip.getId(),
                new StopPointRequest("Bến 1", null, "PICKUP", null));
        StopPointResponse second = stopPointService.create(trip.getId(),
                new StopPointRequest("Bến 2", null, "PICKUP", null));
        stopPointService.create(trip.getId(), new StopPointRequest("Bến 3", null, "PICKUP", null));

        stopPointService.delete(second.id());

        List<StopPointResponse> remaining = stopPointService.listByTrip(trip.getId());
        assertThat(remaining).extracting(StopPointResponse::stopOrder).containsExactly(1, 2);
        assertThat(remaining).extracting(StopPointResponse::name).containsExactly("Bến 1", "Bến 3");
        assertThat(first.stopOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("Điểm đón và điểm trả đánh số riêng, không lẫn vào nhau")
    void pickupAndDropoffAreNumberedSeparately() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        stopPointService.create(trip.getId(), new StopPointRequest("Đón 1", null, "PICKUP", null));
        StopPointResponse dropoff = stopPointService.create(trip.getId(),
                new StopPointRequest("Trả 1", null, "DROPOFF", null));
        stopPointService.create(trip.getId(), new StopPointRequest("Đón 2", null, "PICKUP", null));

        assertThat(dropoff.stopOrder()).isEqualTo(1);
        assertThat(stopPointService.listByTrip(trip.getId())).hasSize(3);
    }

    @Test
    @DisplayName("Đổi loại điểm thì đánh lại số thứ tự cả nhóm cũ lẫn nhóm mới")
    void changingPointTypeRenumbersBothGroups() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        stopPointService.create(trip.getId(), new StopPointRequest("Đón 1", null, "PICKUP", null));
        StopPointResponse moving = stopPointService.create(trip.getId(),
                new StopPointRequest("Đón 2", null, "PICKUP", null));
        stopPointService.create(trip.getId(), new StopPointRequest("Đón 3", null, "PICKUP", null));

        // Đưa điểm giữa của nhóm PICKUP sang DROPOFF.
        stopPointService.update(moving.id(),
                new StopPointRequest("Trả 1", null, "DROPOFF", null));

        List<StopPointResponse> pickups = pointTypeList(trip.getId(), PointType.PICKUP);
        List<StopPointResponse> dropoffs = pointTypeList(trip.getId(), PointType.DROPOFF);

        // Nhóm cũ vừa mất một chỗ: nếu chỉ đánh lại nhóm mới thì còn 1, 3.
        assertThat(pickups).extracting(StopPointResponse::stopOrder).containsExactly(1, 2);
        assertThat(pickups).extracting(StopPointResponse::name)
                .containsExactly("Đón 1", "Đón 3");
        assertThat(dropoffs).extracting(StopPointResponse::stopOrder).containsExactly(1);
        assertThat(dropoffs).extracting(StopPointResponse::name).containsExactly("Trả 1");
    }

    private List<StopPointResponse> pointTypeList(Integer tripId, PointType type) {
        return stopPointService.listByTrip(tripId).stream()
                .filter(point -> point.pointType().equals(type.name()))
                .toList();
    }

    @Test
    @DisplayName("Danh sách điểm trả về điểm đón trước, dễ vẽ ra hành trình")
    void stopPointListPutsPickupFirst() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        stopPointService.create(trip.getId(), new StopPointRequest("Trả", null, "DROPOFF", null));
        stopPointService.create(trip.getId(), new StopPointRequest("Đón", null, "PICKUP", null));

        List<StopPointResponse> points = stopPointService.listByTrip(trip.getId());

        assertThat(points).extracting(StopPointResponse::pointType)
                .containsExactly(PointType.PICKUP.name(), PointType.DROPOFF.name());
    }

    @Test
    @DisplayName("Điểm dừng của chuyến khác thì báo 404, không sửa nhầm")
    void stopPointMustBelongToTripInPath() {
        Trip tripA = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        Trip tripB = createTrip(LocalDateTime.of(2026, 10, 5, 7, 0), BusType.LIMOUSINE_29);
        StopPointResponse point = stopPointService.create(tripB.getId(),
                new StopPointRequest("Bến của B", null, "PICKUP", null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        stopPointService.requireBelongsToTrip(point.id(), tripA.getId()))
                .isInstanceOf(com.ridebooking.booking.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Không thêm được điểm dừng vào chuyến đã huỷ")
    void cannotAddStopPointToCancelledTrip() {
        Trip trip = createTrip(LocalDateTime.of(2026, 10, 5, 6, 0), BusType.LIMOUSINE_29);
        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        stopPointService.create(trip.getId(),
                                new StopPointRequest("Bến", null, "PICKUP", null)))
                .isInstanceOf(ConflictException.class);
    }


    private Trip createTrip(LocalDateTime departure, BusType busType) {
        return createTrip(route, departure, busType);
    }

    private Trip createTrip(Route targetRoute, LocalDateTime departure, BusType busType) {
        Trip trip = new Trip();
        trip.setRoute(targetRoute);
        trip.setCompany(company);
        trip.setBusType(busType);
        trip.setDepartureTime(departure);
        trip.setArrivalTime(departure.plusMinutes(targetRoute.getDurationMinutes()));
        trip.setBasePrice(new BigDecimal("500000"));
        trip.setStatus(TripStatus.ACTIVE);
        trip = tripRepository.saveAndFlush(trip);

        tripSeatRepository.saveAll(seatGenerator.generate(trip));
        return trip;
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }

    private TripRequest tripRequest(BusType busType) {
        return new TripRequest(route.getId(), company.getId(), busType.name(),
                LocalDateTime.of(2026, 10, 5, 6, 0),
                LocalDateTime.of(2026, 10, 5, 12, 0),
                new BigDecimal("250000"), null);
    }
}