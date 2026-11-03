package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.PopularRouteResponse;
import com.ridebooking.booking.dto.RouteRequest;
import com.ridebooking.booking.dto.RouteResponse;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BookingDetailRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý tuyến đường. */
@Service
@Transactional(readOnly = true)
public class RouteService {

    static final int SUGGEST_LIMIT = 20;
    static final int POPULAR_MAX = 20;

    /** Vé đã thu tiền. Đơn PENDING chưa chắc thành vé, đơn CANCELLED đã trả ghế. */
    private static final List<BookingStatus> SOLD_STATUSES = List.of(BookingStatus.PAID, BookingStatus.COMPLETED);

    private final RouteRepository routeRepository;
    private final LocationRepository locationRepository;
    private final TripRepository tripRepository;
    private final BookingDetailRepository bookingDetailRepository;

    public RouteService(RouteRepository routeRepository,
                        LocationRepository locationRepository,
                        TripRepository tripRepository,
                        BookingDetailRepository bookingDetailRepository) {
        this.routeRepository = routeRepository;
        this.locationRepository = locationRepository;
        this.tripRepository = tripRepository;
        this.bookingDetailRepository = bookingDetailRepository;
    }

    /**
     * Tuyến nổi bật trước, phần còn lại lấy từ tuyến bán nhiều vé. Trong mỗi nhóm,
     * tuyến bán nhiều hơn lên trước, bằng nhau thì theo tên để thứ tự ổn định.
     */
    public List<PopularRouteResponse> popular(int limit) {
        int safeLimit = Math.clamp(limit, 1, POPULAR_MAX);

        List<Route> featured = new ArrayList<>(routeRepository.findFeaturedWithLocations());
        Set<Integer> ids = new LinkedHashSet<>();
        featured.forEach(route -> ids.add(route.getId()));
        // Lấy dư để vẫn đủ chỗ khi các tuyến bán chạy trùng với tuyến nổi bật.
        for (Object[] row : bookingDetailRepository.countSoldByRoute(
                SOLD_STATUSES, PageRequest.of(0, safeLimit + featured.size()))) {
            ids.add((Integer) row[0]);
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        List<Route> routes = routeRepository.findByIdInWithLocations(ids);
        Map<Integer, Long> sold = new HashMap<>();
        for (Object[] row : bookingDetailRepository.countSoldOfRoutes(SOLD_STATUSES, ids)) {
            sold.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        Map<Integer, BigDecimal> minPrices = new HashMap<>();
        for (Object[] row : tripRepository.findMinPriceByRouteIds(ids, TripStatus.ACTIVE, LocalDateTime.now())) {
            minPrices.put((Integer) row[0], (BigDecimal) row[1]);
        }

        return routes.stream()
                .sorted(Comparator.comparing(Route::isFeatured).reversed()
                        .thenComparing((Route route) -> sold.getOrDefault(route.getId(), 0L), Comparator.reverseOrder())
                        .thenComparing(Route::getName))
                .limit(safeLimit)
                .map(route -> PopularRouteResponse.of(route,
                        sold.getOrDefault(route.getId(), 0L), minPrices.get(route.getId())))
                .toList();
    }

    /** Các tuyến đi ra từ một địa điểm — nguồn cho block "Tuyến đường phổ biến". */
    public List<RouteResponse> popularFrom(Integer locationId) {
        return routeRepository.findByOriginIdWithLocations(locationId, PageRequest.of(0, SUGGEST_LIMIT))
                .stream().map(RouteResponse::from).toList();
    }

    /** Các tuyến có liên quan tới một địa điểm, kể cả chiều đi vào. */
    public List<RouteResponse> touchingLocation(Integer locationId) {
        return routeRepository.findTouchingLocation(locationId, PageRequest.of(0, SUGGEST_LIMIT))
                .stream().map(RouteResponse::from).toList();
    }

    public List<RouteResponse> listAll() {
        return routeRepository.findAll().stream()
                .map(RouteResponse::from)
                .toList();
    }

    public RouteResponse getById(Integer id) {
        return RouteResponse.from(requireById(id));
    }

    @Transactional
    public RouteResponse create(RouteRequest request) {
        String name = request.name().trim();

        // Kiểm tra ở tầng service để trả 409 với thông điệp rõ ràng. Nếu chỉ dựa
        // vào CHECK/UNIQUE ở DB thì client nhận 500 do vi phạm ràng buộc.
        if (routeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Tuyến '" + name + "' đã tồn tại");
        }
        if (request.originId().equals(request.destinationId())) {
            throw new IllegalArgumentException("Điểm xuất phát và điểm đến không được là một");
        }

        Route route = new Route();
        apply(route, request, name);
        return RouteResponse.from(routeRepository.save(route));
    }

    @Transactional
    public RouteResponse update(Integer id, RouteRequest request) {
        Route route = requireById(id);
        String name = request.name().trim();

        if (request.originId().equals(request.destinationId())) {
            throw new IllegalArgumentException("Điểm xuất phát và điểm đến không được là một");
        }
        routeRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Tuyến '" + name + "' đã tồn tại");
                });

        apply(route, request, name);
        return RouteResponse.from(routeRepository.save(route));
    }

    @Transactional
    public void delete(Integer id) {
        routeRepository.delete(requireById(id));
    }

    private void apply(Route route, RouteRequest request, String name) {
        route.setName(name);
        route.setOrigin(requireLocation(request.originId(), "điểm xuất phát"));
        route.setDestination(requireLocation(request.destinationId(), "điểm đến"));
        route.setDistanceKm(request.distanceKm());
        route.setDurationMinutes(request.durationMinutes());
    }

    private Location requireLocation(Integer id, String label) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy " + label + " với id " + id));
    }

    private Route requireById(Integer id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến với id " + id));
    }
}