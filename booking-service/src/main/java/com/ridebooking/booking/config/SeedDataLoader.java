package com.ridebooking.booking.config;

import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.StopPoint;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.PointType;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.StopPointRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import com.ridebooking.booking.service.LocationService;
import com.ridebooking.booking.service.SeatGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp dữ liệu mẫu cho môi trường dev.
 *
 * <p>Bật bằng {@code app.seed.enabled=true}. Mặc định TẮT, nên chạy thật không bị
 * đổ vào dữ liệu mẫu ngoài ý muốn.
 *
 * <p>Ghế được sinh bằng {@link SeatGenerator}, tức cùng hàm {@link BusType#layout()}
 * mà lúc tạo chuyến dùng. Không viết ghế bằng SQL {@code generate_series}: sơ đồ
 * ghế sẽ có hai nơi khai, và mỗi lần sửa sơ đồ là một lần dữ liệu mẫu lệch —
 * đúng loại lỗi đã xảy ra khi {@code LIMOUSINE_29} sinh 49 ghế.
 *
 * <p>Chạy lại nhiều lần vẫn an toàn: nếu đã có địa điểm thì bỏ qua, không nhân bản.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    /** Ngày đầu tiên có chuyến. Hôm nay không nạp: dữ liệu mẫu còn dùng được ngày mai. */
    private static final int FIRST_DAY_OFFSET = 1;

    /** Số ngày có chuyến. 5 ngày đủ cho cả tuần làm việc của đồ án. */
    private static final int TRIP_DAYS = 5;

    private final LocationRepository locationRepository;
    private final BusCompanyRepository busCompanyRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final TripSeatRepository tripSeatRepository;
    private final StopPointRepository stopPointRepository;
    private final SeatGenerator seatGenerator;

    public SeedDataLoader(LocationRepository locationRepository,
                          BusCompanyRepository busCompanyRepository,
                          RouteRepository routeRepository,
                          TripRepository tripRepository,
                          TripSeatRepository tripSeatRepository,
                          StopPointRepository stopPointRepository,
                          SeatGenerator seatGenerator) {
        this.locationRepository = locationRepository;
        this.busCompanyRepository = busCompanyRepository;
        this.routeRepository = routeRepository;
        this.tripRepository = tripRepository;
        this.tripSeatRepository = tripSeatRepository;
        this.stopPointRepository = stopPointRepository;
        this.seatGenerator = seatGenerator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (locationRepository.count() > 0) {
            log.info("Bỏ qua seed: locations đã có dữ liệu");
            return;
        }
        log.info("Bắt đầu nạp dữ liệu mẫu");

        Map<String, Location> locations = seedLocations();
        List<BusCompany> companies = seedCompanies();
        List<Route> routes = seedRoutes(locations);

        int tripCount = 0;
        int seatCount = 0;
        for (Route route : routes) {
            for (LocalDate day = LocalDate.now().plusDays(FIRST_DAY_OFFSET);
                    day.isBefore(LocalDate.now().plusDays(FIRST_DAY_OFFSET + TRIP_DAYS));
                    day = day.plusDays(1)) {
                for (int slot = 0; slot < 3; slot++) {
                    Trip trip = seedTrip(route, companies.get((tripCount) % companies.size()),
                            day, slot, tripCount);
                    seatCount += tripSeatRepository.saveAll(seatGenerator.generate(trip)).size();
                    seedStopPoints(trip, route);
                    tripCount++;
                }
            }
        }
        log.info("Xong seed: {} chuyến, {} ghế, {} tuyến, {} địa điểm, {} nhà xe",
                tripCount, seatCount, routes.size(), locations.size(), companies.size());
    }

    /** Thứ tự cố ý phổ biến trước, vì id nhỏ thì dropdown dễ kiểm thử. */
    private Map<String, Location> seedLocations() {
        Map<String, Location> result = new LinkedHashMap<>();
        String[] names = {
                "Hà Nội", "Hồ Chí Minh", "Hải Phòng", "Đà Nẵng", "Hội An", "Cần Thơ",
                "Huế", "Nha Trang", "Vũng Tàu", "Hạ Long",
                "Sa Pa", "Đà Lạt", "Buôn Ma Thuột", "Pleiku", "Quy Nhơn",
                "Thanh Hóa", "Nam Định", "Vinh", "Hà Giang", "Tuyên Quang",
        };
        for (String name : names) {
            // Trùng tên là lỗi ngay ở đây: slug phái từ tên nên hai dòng cùng tên
            // sẽ đụng uk_locations_slug và làm dừng cả quá trình seed.
            Location location = new Location();
            location.setName(name);
            location.setSlug(LocationService.resolveSlug(null, name));
            result.put(name, locationRepository.save(location));
        }
        return result;
    }

    private List<BusCompany> seedCompanies() {
        List<BusCompany> companies = new ArrayList<>();
        String[][] rows = {
                {"G8 SAPA OPEN TOUR", "Xe limousine 9 chỗ và 11 chỗ, tuyến Tây Bắc."},
                {"HATOUR", "Xe giường nằm Hà Nội - miền Nam, đi nhiều chuyến trong ngày."},
                {"KINGBUS", "Hạng trọng 2 tầng, có Wi-Fi và ổ cắm sạc."},
                {"DUONG DAT", "Xe ghế ngồi, phù hợp tuyến nội thành và đường ngắn."},
                {"SAI GON - HANOI", "Xe giường nằm chạy Bắc - Nam, đón trực tiếp tại nhà."},
                {"AN PHU", "Xe ghế mềm tuyến miền Trung."},
        };
        for (String[] row : rows) {
            BusCompany company = new BusCompany();
            company.setName(row[0]);
            company.setDescription(row[1]);
            companies.add(busCompanyRepository.save(company));
        }
        return companies;
    }

    private List<Route> seedRoutes(Map<String, Location> locations) {
        List<Route> routes = new ArrayList<>();
        // Tên, điểm đi, điểm đến, km, phút
        Object[][] rows = {
                {"Hà Nội - Sa Pa", "Hà Nội", "Sa Pa", "465", "510"},
                {"Sa Pa - Hà Nội", "Sa Pa", "Hà Nội", "465", "510"},
                {"Hà Nội - Hạ Long", "Hà Nội", "Hạ Long", "165", "180"},
                {"Hà Nội - Hải Phòng", "Hà Nội", "Hải Phòng", "120", "150"},
                {"Hà Nội - Nam Định", "Hà Nội", "Nam Định", "60", "75"},
                {"Hà Nội - Thanh Hóa", "Hà Nội", "Thanh Hóa", "185", "220"},
                {"Hà Nội - Vinh", "Hà Nội", "Vinh", "285", "330"},
                {"Hà Nội - Đà Nẵng", "Hà Nội", "Đà Nẵng", "845", "960"},
                {"Đà Nẵng - Huế", "Đà Nẵng", "Huế", "85", "105"},
                {"Hội An - Huế", "Hội An", "Huế", "85", "105"},
                {"Hồ Chí Minh - Vũng Tàu", "Hồ Chí Minh", "Vũng Tàu", "100", "120"},
                {"Hồ Chí Minh - Nha Trang", "Hồ Chí Minh", "Nha Trang", "415", "480"},
                {"Hồ Chí Minh - Đà Lạt", "Hồ Chí Minh", "Đà Lạt", "300", "390"},
                {"Hồ Chí Minh - Cần Thơ", "Hồ Chí Minh", "Cần Thơ", "165", "200"},
                {"Đà Nẵng - Đà Lạt", "Đà Nẵng", "Đà Lạt", "245", "330"},
        };
        for (Object[] row : rows) {
            Route route = new Route();
            route.setName((String) row[0]);
            route.setOrigin(locations.get(row[1]));
            route.setDestination(locations.get(row[2]));
            route.setDistanceKm(new BigDecimal((String) row[3]));
            route.setDurationMinutes(Integer.parseInt((String) row[4]));
            routes.add(routeRepository.save(route));
        }
        return routes;
    }

    /** Ba khung giờ mỗi ngày, xen kẳ loại xe và nhà xe để dữ liệu mẫu có đủ nét. */
    private Trip seedTrip(Route route, BusCompany company, LocalDate day, int slot, int index) {
        BusType busType = switch (index % 3) {
            case 0 -> BusType.LIMOUSINE_29;
            case 1 -> BusType.SLEEPER_45;
            default -> BusType.CABIN_DOUBLE_22;
        };
        LocalTime departure = LocalTime.of(6 + slot * 5, 0);
        int duration = route.getDurationMinutes();

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setCompany(company);
        trip.setBusType(busType);
        trip.setDepartureTime(LocalDateTime.of(day, departure));
        trip.setArrivalTime(trip.getDepartureTime().plusMinutes(duration));
        // Giá cơ bản suy từ quãng đường: 12.000đ/km, làm tròn nghìn.
        BigDecimal price = route.getDistanceKm()
                .multiply(new BigDecimal("12000"))
                .setScale(0, java.math.RoundingMode.HALF_UP);
        trip.setBasePrice(busType == BusType.LIMOUSINE_29 ? price.multiply(new BigDecimal("1.6")) : price);
        trip.setStatus(TripStatus.ACTIVE);
        // Cần id trước khi sinh ghế.
        return tripRepository.saveAndFlush(trip);
    }

    /** Hai điểm: đón ở bên đi, trả ở bên đến. */
    private void seedStopPoints(Trip trip, Route route) {
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