package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.RouteRequest;
import com.ridebooking.booking.dto.RouteResponse;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý tuyến đường. */
@Service
@Transactional(readOnly = true)
public class RouteService {

    static final int SUGGEST_LIMIT = 20;

    private final RouteRepository routeRepository;
    private final LocationRepository locationRepository;

    public RouteService(RouteRepository routeRepository, LocationRepository locationRepository) {
        this.routeRepository = routeRepository;
        this.locationRepository = locationRepository;
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