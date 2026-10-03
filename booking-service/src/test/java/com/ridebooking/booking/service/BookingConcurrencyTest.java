package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Kiểm chứng khoá dòng chống bán trùng ghế bằng hai luồng giao dịch THẬT.
 *
 * <p>Không dùng {@code @Transactional} ở lớp: mỗi luồng phải có transaction riêng
 * thì khoá dòng mới có ý nghĩa. Vì vậy test tự dọn dữ liệu bằng tay và dùng một
 * database H2 riêng để không lẫn với các lớp test khác.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bookingconcurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
        "app.booking.expiry-job.enabled=false"})
class BookingConcurrencyTest {

    @Autowired private BookingService bookingService;

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
    private Integer seatId;

    @BeforeEach
    void setUp() {
        cleanUp();

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

        seatId = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()).get(0).getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("Hai người cùng lúc giành một ghế: đúng một người thắng, người kia nhận 409")
    void onlyOneOfTwoConcurrentHoldsWins() throws Exception {
        int contenders = 2;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < contenders; i++) {
            final int userId = 1000 + i;
            Callable<Boolean> task = () -> {
                startGun.await();
                try {
                    bookingService.holdSeats(userId, holdRequest(seatId));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                }
            };
            futures.add(pool.submit(task));
        }

        startGun.countDown();
        int successes = 0;
        int conflicts = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(30, TimeUnit.SECONDS)) {
                successes++;
            } else {
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
        assertThat(bookingRepository.count()).isEqualTo(1);

        TripSeat seat = tripSeatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HOLDING);
        assertThat(seat.getHoldingUntil()).isAfter(LocalDateTime.now());
    }

    private HoldSeatsRequest holdRequest(Integer id) {
        return new HoldSeatsRequest(trip.getId(), List.of(id), null, null,
                "Khách tranh chỗ", "0901234567", "a@example.com");
    }

    private void cleanUp() {
        paymentRepository.deleteAll();
        bookingDetailRepository.deleteAll();
        bookingRepository.deleteAll();
        tripSeatRepository.deleteAll();
        tripRepository.deleteAll();
        routeRepository.deleteAll();
        busCompanyRepository.deleteAll();
        locationRepository.deleteAll();
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }
}
