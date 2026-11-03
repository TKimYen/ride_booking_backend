package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BusType;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * [L3] Kiểm tra không cho đặt vé chuyến đã khởi hành — HTTP 409 Conflict.
 *
 * <p>Tách riêng khỏi {@link BookingFlowTest} để tránh chạy chung các test
 * concurrency nặng có PESSIMISTIC lock gây treo trong H2 in-memory.
 */
@SpringBootTest
@Transactional
class DepartedTripTest {

    private static final int USER = 101;

    @Autowired private BookingService bookingService;

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BookingDetailRepository bookingDetailRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private SeatGenerator seatGenerator;

    private Trip departedTrip;
    private List<TripSeat> departedSeats;

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

        Location hn = new Location();
        hn.setName("Hà Nội");
        hn.setSlug("ha-noi");
        hn = locationRepository.save(hn);

        Location sp = new Location();
        sp.setName("Sa Pa");
        sp.setSlug("sa-pa");
        sp = locationRepository.save(sp);

        BusCompany company = new BusCompany();
        company.setName("Nhà xe test L3");
        company = busCompanyRepository.save(company);

        Route route = new Route();
        route.setName("Hà Nội - Sa Pa");
        route.setOrigin(hn);
        route.setDestination(sp);
        route = routeRepository.save(route);

        // Chuyến đã khởi hành 1 giờ trước
        departedTrip = new Trip();
        departedTrip.setRoute(route);
        departedTrip.setCompany(company);
        departedTrip.setBusType(BusType.LIMOUSINE_29);
        departedTrip.setDepartureTime(LocalDateTime.now().minusHours(1));
        departedTrip.setArrivalTime(LocalDateTime.now().plusHours(5));
        departedTrip.setBasePrice(new BigDecimal("250000"));
        departedTrip.setStatus(TripStatus.ACTIVE);
        departedTrip = tripRepository.saveAndFlush(departedTrip);
        departedSeats = tripSeatRepository.saveAllAndFlush(seatGenerator.generate(departedTrip));
    }

    @Test
    @DisplayName("[L3] Không cho đặt vé chuyến đã chạy — phải trả lỗi 409 Conflict")
    void holdSeats_departedTrip_throwsConflictException() {
        HoldSeatsRequest request = new HoldSeatsRequest(
                departedTrip.getId(),
                List.of(departedSeats.get(0).getId()),
                null, null,
                "Khách Test", "0901234567", "test@example.com"
        );

        assertThatThrownBy(() -> bookingService.holdSeats(USER, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Chuyến xe đã khởi hành, không thể đặt vé");
    }
}

