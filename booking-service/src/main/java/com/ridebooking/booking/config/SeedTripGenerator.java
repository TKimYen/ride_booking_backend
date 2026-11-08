package com.ridebooking.booking.config;

import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.StopPoint;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.PointType;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.StopPointRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import com.ridebooking.booking.service.SeatGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sinh chuyến mẫu: 3 chuyến mỗi tuyến mỗi ngày, kèm ghế và điểm đón/trả.
 *
 * <p>Dùng chung cho lần nạp đầu ({@link SeedDataLoader}) và bước bù chuyến
 * ({@link SeedTripTopUpJob}), nên dữ liệu bù giống hệt dữ liệu gốc.
 *
 * <p>Ngày nào tuyến đã có chuyến (do seed trước, admin tạo, hay đã huỷ) thì bỏ
 * qua cả ngày đó. Gọi lại bao nhiêu lần cũng không tạo trùng.
 */
@Component
public class SeedTripGenerator {

    /** Ba khung giờ mỗi ngày: 06:00, 11:00, 16:00. */
    static final int SLOTS_PER_DAY = 3;

    /** Chặn cấu hình nhầm (vd. 3650) làm sinh hàng triệu ghế. */
    static final int MAX_TRIP_DAYS = 90;

    private final TripRepository tripRepository;
    private final TripSeatRepository tripSeatRepository;
    private final StopPointRepository stopPointRepository;
    private final RouteRepository routeRepository;
    private final BusCompanyRepository busCompanyRepository;
    private final SeatGenerator seatGenerator;
    private final int tripDays;

    public SeedTripGenerator(TripRepository tripRepository,
                             TripSeatRepository tripSeatRepository,
                             StopPointRepository stopPointRepository,
                             RouteRepository routeRepository,
                             BusCompanyRepository busCompanyRepository,
                             SeatGenerator seatGenerator,
                             @Value("${app.seed.trip-days:5}") int tripDays) {
        if (tripDays < 1 || tripDays > MAX_TRIP_DAYS) {
            throw new IllegalStateException(
                    "SEED_TRIP_DAYS phải từ 1 đến " + MAX_TRIP_DAYS + ", đang là " + tripDays);
        }
        this.tripRepository = tripRepository;
        this.tripSeatRepository = tripSeatRepository;
        this.stopPointRepository = stopPointRepository;
        this.routeRepository = routeRepository;
        this.busCompanyRepository = busCompanyRepository;
        this.seatGenerator = seatGenerator;
        this.tripDays = tripDays;
    }

    public int tripDays() {
        return tripDays;
    }

    /** Kết quả một lần bù: số chuyến và số ghế vừa tạo. */
    public record TopUpResult(int trips, int seats) {
    }

    /**
     * Bù chuyến cho mọi tuyến trong DB, từ ngày mai tới {@code tripDays} ngày.
     * Hôm nay không bù: một nửa khung giờ đã qua.
     */
    @Transactional
    public TopUpResult topUpAll(LocalDate today) {
        List<Route> routes = routeRepository.findAll();
        List<BusCompany> companies = busCompanyRepository.findAll().stream()
                .sorted(Comparator.comparing(BusCompany::getId))
                .toList();
        return topUp(routes, companies, today.plusDays(1), tripDays);
    }

    @Transactional
    public TopUpResult topUp(List<Route> routes, List<BusCompany> companies, LocalDate firstDay, int days) {
        if (companies.isEmpty()) {
            return new TopUpResult(0, 0);
        }
        int trips = 0;
        int seats = 0;
        for (Route route : routes) {
            if (route.getDurationMinutes() == null || route.getDistanceKm() == null) {
                continue; // tuyến admin tạo thiếu dữ liệu thì không đoán giờ đến / giá
            }
            for (LocalDate day = firstDay; day.isBefore(firstDay.plusDays(days)); day = day.plusDays(1)) {
                if (tripRepository.existsByRouteIdAndDepartureTimeBetween(
                        route.getId(), day.atStartOfDay(), day.atTime(LocalTime.MAX))) {
                    continue;
                }
                for (int slot = 0; slot < SLOTS_PER_DAY; slot++) {
                    // Chỉ số cố định theo (tuyến, ngày, khung giờ): bù lại ngày nào cũng ra
                    // cùng nhà xe và loại xe, không phụ thuộc thứ tự chạy.
                    int index = route.getId() * 7 + (int) day.toEpochDay() * SLOTS_PER_DAY + slot;
                    Trip trip = createTrip(route, companies.get(index % companies.size()), day, slot, index);
                    seats += tripSeatRepository.saveAll(seatGenerator.generate(trip)).size();
                    createStopPoints(trip, route);
                    trips++;
                }
            }
        }
        return new TopUpResult(trips, seats);
    }

    /** Xen kẽ loại xe để dữ liệu mẫu có đủ nét. */
    private Trip createTrip(Route route, BusCompany company, LocalDate day, int slot, int index) {
        BusType busType = switch (index % 3) {
            case 0 -> BusType.LIMOUSINE_29;
            case 1 -> BusType.SLEEPER_45;
            default -> BusType.CABIN_DOUBLE_22;
        };
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setCompany(company);
        trip.setBusType(busType);
        trip.setDepartureTime(LocalDateTime.of(day, LocalTime.of(6 + slot * 5, 0)));
        trip.setArrivalTime(trip.getDepartureTime().plusMinutes(route.getDurationMinutes()));
        // Giá cơ bản suy từ quãng đường: 12.000đ/km, làm tròn nghìn.
        BigDecimal price = route.getDistanceKm()
                .multiply(new BigDecimal("12000"))
                .setScale(0, RoundingMode.HALF_UP);
        trip.setBasePrice(busType == BusType.LIMOUSINE_29 ? price.multiply(new BigDecimal("1.6")) : price);
        trip.setStatus(TripStatus.ACTIVE);
        // Cần id trước khi sinh ghế.
        return tripRepository.saveAndFlush(trip);
    }

    /** Hai điểm: đón ở bên đi, trả ở bên đến. */
    private void createStopPoints(Trip trip, Route route) {
        StopPoint pickup = new StopPoint();
        pickup.setTrip(trip);
        pickup.setName("Bến xe " + route.getOrigin().getName());
        pickup.setAddress("Bến xe trung tâm " + route.getOrigin().getName());
        pickup.setPointType(PointType.PICKUP);
        pickup.setStopOrder(1);
        pickup.setEstimatedTime(trip.getDepartureTime().minusMinutes(10));

        StopPoint dropoff = new StopPoint();
        dropoff.setTrip(trip);
        dropoff.setName("Bến xe " + route.getDestination().getName());
        dropoff.setAddress("Bến xe trung tâm " + route.getDestination().getName());
        dropoff.setPointType(PointType.DROPOFF);
        dropoff.setStopOrder(1);
        dropoff.setEstimatedTime(trip.getArrivalTime());

        stopPointRepository.saveAll(List.of(pickup, dropoff));
    }
}
