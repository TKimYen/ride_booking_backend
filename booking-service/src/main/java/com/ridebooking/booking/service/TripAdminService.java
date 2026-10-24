package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.TripRequest;
import com.ridebooking.booking.dto.TripResponse;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo và sửa chuyến xe cho Trang Quản lý Chuyến xe.
 *
 * <p>Việc ghi ghế nằm trong cùng transaction với việc tạo chuyến, cố tình vậy.
 * Nếu tạo chuyến xong mới ghi ghế ở transaction sau, thì giữa hai lần gọi đó có
 * một chuyến không ghế nào — và API sơ đồ ghế trả về danh sách rỗng, khách thấy
 * chuyến "hết chỗ" trong khi thực ra xe còn trống. Lỗi đó khó đoán vì dữ liệu vẫn
 * đúng, chỉ là thiếu tạm thời.
 */
@Service
@Transactional
public class TripAdminService {

    private final TripRepository tripRepository;
    private final TripSeatRepository tripSeatRepository;
    private final RouteRepository routeRepository;
    private final BusCompanyRepository busCompanyRepository;
    private final SeatGenerator seatGenerator;

    public TripAdminService(TripRepository tripRepository,
                            TripSeatRepository tripSeatRepository,
                            RouteRepository routeRepository,
                            BusCompanyRepository busCompanyRepository,
                            SeatGenerator seatGenerator) {
        this.tripRepository = tripRepository;
        this.tripSeatRepository = tripSeatRepository;
        this.routeRepository = routeRepository;
        this.busCompanyRepository = busCompanyRepository;
        this.seatGenerator = seatGenerator;
    }

    public TripResponse create(TripRequest request) {
        BusType busType = parseBusType(request.busType());
        requireArrivalAfterDeparture(request.departureTime(), request.arrivalTime());

        Trip trip = new Trip();
        trip.setRoute(requireRoute(request.routeId()));
        trip.setCompany(requireCompany(request.companyId()));
        trip.setBusType(busType);
        trip.setDepartureTime(request.departureTime());
        trip.setArrivalTime(request.arrivalTime());
        trip.setBasePrice(request.basePrice());
        trip.setStatus(parseStatus(request.status()));

        // Cần id trước khi sinh ghế: trip_seats có khoá ngoại trip_id.
        tripRepository.saveAndFlush(trip);
        List<TripSeat> seats = seatGenerator.generate(trip);
        tripSeatRepository.saveAll(seats);

        return TripResponse.from(trip, seats.size());
    }

    public TripResponse update(Integer id, TripRequest request) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + id));
        BusType busType = parseBusType(request.busType());
        requireArrivalAfterDeparture(request.departureTime(), request.arrivalTime());

        // ĐỔI LOẠI XE SAU KHI ĐÃ CÓ KHÁCH là việc nguy hiểm: sơ đồ ghế cũ không
        // khớp loại xe mới. Từ chối thay vì âm thầm sinh lại ghế.
        if (trip.getBusType() != busType) {
            requireNoSeatsTaken(id);
            // Gán loại xe mới VÀO TRƯỚC khi sinh ghế. SeatGenerator đọc loại xe từ
            // đối tượng trip, nếu sinh trước rồi gán sau thì ghế ra theo loại xe CŨ:
            // đổi sang xe 45 chỗ mà sơ đồ vẫn 29 ghế.
            trip.setBusType(busType);
            replaceSeats(trip);
        }

        trip.setRoute(requireRoute(request.routeId()));
        trip.setCompany(requireCompany(request.companyId()));
        trip.setBusType(busType);
        trip.setDepartureTime(request.departureTime());
        trip.setArrivalTime(request.arrivalTime());
        trip.setBasePrice(request.basePrice());
        trip.setStatus(parseStatus(request.status()));
        tripRepository.saveAndFlush(trip);

        return TripResponse.from(trip, countSeats(id));
    }

    /**
     * Xoá chuyến.
     *
     * <p>Từ chối khi đã bán vé. {@code bookings} khoá ngoại {@code RESTRICT} cũng
     * chặn, nhưng lỗi DB trả 500 với thông điệp không dùng được, nên kiểm tra ở
     * đây để Admin hiểu phải làm gì.
     */
    public void delete(Integer id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + id));
        requireNoSeatsTaken(id);
        tripRepository.delete(trip);
    }

    private void requireNoSeatsTaken(Integer tripId) {
        long sold = tripSeatRepository.countByTripIdAndStatus(tripId, SeatStatus.BOOKED);
        if (sold > 0) {
            throw new ConflictException(
                    "Chuyến đã bán " + sold + " ghế, hãy huỷ chuyến thay vì xoá hoặc đổi loại xe");
        }
        long holding = tripSeatRepository.countByTripIdAndStatus(tripId, SeatStatus.HOLDING);
        if (holding > 0) {
            throw new ConflictException("Chuyến đang có " + holding
                    + " ghế được giữ, đợi hết giờ giữ rồi hãy làm tiếp");
        }
    }

    /**
     * Đặt lại ghế theo loại xe của {@code trip}. Chỉ gọi sau
     * {@link #requireNoSeatsTaken} và sau khi {@code trip.busType} đã là loại mới.
     */
    private void replaceSeats(Trip trip) {
        tripSeatRepository.deleteAll(
                tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()));
        // flush trước khi insert: nếu không, DELETE và INSERT cùng lô thì vẫn còn
        // dòng cũ khi Hibernate kiểm tra khoá duy nhất (trip_id, seat_name).
        tripSeatRepository.flush();
        tripSeatRepository.saveAll(seatGenerator.generate(trip));
    }

    private int countSeats(Integer tripId) {
        return tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(tripId).size();
    }

    private Route requireRoute(Integer id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến với id " + id));
    }

    private BusCompany requireCompany(Integer id) {
        return busCompanyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà xe với id " + id));
    }

    /**
     * Enum không khớp thì báo đúng danh sách hợp lệ.
     *
     * <p>Không để Jackson ném lỗi parse mặc định vì thông điệp kiểu "no enum
     * constant" khiến Admin không biết cần nhập gì.
     */
    static BusType parseBusType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Loại xe không được bỏ trống");
        }
        return matchOrExplain(BusType.values(), raw, "Loại xe",
                Arrays.stream(BusType.values()).map(BusType::name).collect(Collectors.joining(", ")));
    }

    static TripStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return TripStatus.ACTIVE;
        }
        return matchOrExplain(TripStatus.values(), raw, "Trạng thái",
                Arrays.stream(TripStatus.values()).map(TripStatus::name).collect(Collectors.joining(", ")));
    }

    private static <E extends Enum<E>> E matchOrExplain(E[] values, String raw, String label, String allowed) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(raw.trim())) {
                return value;
            }
        }
        throw new IllegalArgumentException(label + " '" + raw + "' không hợp lệ. Chỉ nhận: " + allowed);
    }

    static void requireArrivalAfterDeparture(LocalDateTime departure, LocalDateTime arrival) {
        if (!arrival.isAfter(departure)) {
            throw new IllegalArgumentException("Giờ đến phải sau giờ khởi hành");
        }
    }
}