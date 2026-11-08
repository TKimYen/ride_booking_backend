package com.ridebooking.booking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.StopPointRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import com.ridebooking.booking.service.LocationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

/** Bù chuyến mẫu: đủ 3 chuyến mỗi ngày, không trùng, bỏ qua ngày đã có chuyến. */
@SpringBootTest
@Transactional
class SeedTripGeneratorTest {

    private static final LocalDate DAY1 = LocalDate.now().plusDays(200);

    @Autowired private SeedTripGenerator generator;
    @Autowired private ApplicationContext context;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private StopPointRepository stopPointRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private LocationRepository locationRepository;

    private Route route;
    private List<BusCompany> companies;

    @BeforeEach
    void setUp() {
        Location from = locationRepository.save(location("Điểm bù A"));
        Location to = locationRepository.save(location("Điểm bù B"));
        Route r = new Route();
        r.setName("Tuyến bù");
        r.setOrigin(from);
        r.setDestination(to);
        r.setDistanceKm(new BigDecimal("100"));
        r.setDurationMinutes(120);
        route = routeRepository.save(r);
        companies = List.of(company("Nhà xe bù 1"), company("Nhà xe bù 2"));
    }

    @Test
    @DisplayName("Mỗi ngày 3 chuyến, có ghế và điểm đón/trả")
    void createsThreeTripsPerDay() {
        SeedTripGenerator.TopUpResult result = generator.topUp(List.of(route), companies, DAY1, 3);

        assertThat(result.trips()).isEqualTo(9);
        List<Trip> trips = tripsOfRoute();
        assertThat(trips).hasSize(9).allSatisfy(trip -> {
            assertThat(trip.getStatus()).isEqualTo(TripStatus.ACTIVE);
            assertThat(trip.getArrivalTime()).isEqualTo(trip.getDepartureTime().plusMinutes(120));
            assertThat(tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()))
                    .hasSize(trip.getBusType().seatCount());
            assertThat(stopPointRepository.findAll().stream()
                    .filter(p -> p.getTrip().getId().equals(trip.getId()))).hasSize(2);
        });
        assertThat(result.seats()).isEqualTo(trips.stream().mapToInt(t -> t.getBusType().seatCount()).sum());
    }

    @Test
    @DisplayName("Chạy lại không tạo trùng")
    void isIdempotent() {
        generator.topUp(List.of(route), companies, DAY1, 3);

        assertThat(generator.topUp(List.of(route), companies, DAY1, 3).trips()).isZero();
        assertThat(tripsOfRoute()).hasSize(9);
    }

    @Test
    @DisplayName("Ngày đã có chuyến (vd. admin tạo) thì bỏ qua cả ngày")
    void skipsDaysThatAlreadyHaveTrips() {
        Trip existing = new Trip();
        existing.setRoute(route);
        existing.setCompany(companies.getFirst());
        existing.setBusType(BusType.LIMOUSINE_29);
        existing.setDepartureTime(DAY1.plusDays(1).atTime(20, 0));
        existing.setArrivalTime(DAY1.plusDays(1).atTime(22, 0));
        existing.setBasePrice(new BigDecimal("200000"));
        existing.setStatus(TripStatus.ACTIVE);
        tripRepository.saveAndFlush(existing);

        assertThat(generator.topUp(List.of(route), companies, DAY1, 3).trips()).isEqualTo(6);
        assertThat(tripsOfRoute().stream().filter(t -> t.getDepartureTime().toLocalDate().equals(DAY1.plusDays(1))))
                .containsExactly(existing);
    }

    @Test
    @DisplayName("Mặc định không bật bước bù (production)")
    void topUpJobIsOffByDefault() {
        assertThat(context.getBeansOfType(SeedTripTopUpJob.class)).isEmpty();
    }

    private List<Trip> tripsOfRoute() {
        return tripRepository.findAll().stream()
                .filter(t -> t.getRoute().getId().equals(route.getId()))
                .toList();
    }

    private Location location(String name) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(LocationService.resolveSlug(null, name));
        return location;
    }

    private BusCompany company(String name) {
        BusCompany company = new BusCompany();
        company.setName(name);
        return busCompanyRepository.save(company);
    }
}
