package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.TripResponse;
import com.ridebooking.booking.dto.TripSearchItemResponse;
import com.ridebooking.booking.dto.TripSearchRequest;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripSort;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tìm kiếm và đọc chuyến xe. */
@Service
@Transactional(readOnly = true)
public class TripService {

    /** Trần kích thước trang, giống quy ước đã dùng ở user-service. */
    static final int MAX_PAGE_SIZE = 100;

    private final TripRepository tripRepository;
    private final TripSeatRepository tripSeatRepository;

    public TripService(TripRepository tripRepository,
                       TripSeatRepository tripSeatRepository) {
        this.tripRepository = tripRepository;
        this.tripSeatRepository = tripSeatRepository;
    }

    /**
     * Tìm chuyến theo bộ lọc — API chính cho Trang chủ và Trang Danh sách kết quả.
     *
     * <p>Mặc định chỉ trả chuyến {@code ACTIVE} và sắp theo giờ đi. Sắp xếp đi qua
     * allowlist {@link TripSort} nên tên cột không bao giờ do người dùng quyết định.
     */
    public PageResponse<TripSearchItemResponse> search(TripSearchRequest request, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

        TimeWindow window = resolveTimeWindow(request);

        Specification<Trip> spec = TripRepository.matches(
                request.originId(),
                request.destinationId(),
                request.companyId(),
                window.from(),
                window.to(),
                request.minPrice(),
                request.maxPrice(),
                TripStatus.ACTIVE);

        TripSort sort = request.sortOrDefault();
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(sort.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC,
                        sort.attribute()));

        Page<Trip> result = tripRepository.findAll(spec, pageable);
        return PageResponse.from(result, withSeatCounts(result.getContent()));
    }

    /** Chi tiết chuyến cho Trang Thông tin chi tiết. */
    public TripResponse getById(Integer id) {
        Trip trip = requireById(id);
        Map<Integer, Integer> totals = countTotalsFor(List.of(id));
        return TripResponse.from(trip, totals.get(id));
    }

    /**
     * Khoảng thời gian lọc, đã gộp {@code travelDate} và hai mốc giờ.
     *
     * <p>Không thể lọc "giờ khởi hành từ 18:00" mà không cần một mốc ngày, vì
     * {@code departureTime} là một {@code LocalDateTime} đầy đủ. Vì vậy nếu có bộ
     * lọc giờ mà không có ngày thì mặc định lấy HÔM NAY — đúng như người dùng
     * nghĩ khi gõ "chuyến sau 6h chiều".
     *
     * <p>Mốc trên là {@link LocalTime#MAX} ({@code 23:59:59.999999999}) chứ không
     * phải {@code 00:00} ngày hôm sau: so sánh {@code <=} với nửa đêm sẽ loại mất
     * chuyến khởi hành đúng nửa đêm.
     */
    private TimeWindow resolveTimeWindow(TripSearchRequest request) {
        boolean hasTimeFilter = request.earliestDeparture() != null
                || request.latestDeparture() != null;

        LocalDate day = request.travelDate();
        if (day == null && hasTimeFilter) {
            day = LocalDate.now();
        }

        if (day == null) {
            return new TimeWindow(null, null);
        }

        LocalDateTime from = request.earliestDeparture() == null
                ? day.atStartOfDay()
                : day.atTime(request.earliestDeparture());
        LocalDateTime to = request.latestDeparture() == null
                ? day.atTime(LocalTime.MAX)
                : day.atTime(request.latestDeparture());

        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Giờ khởi hành muộn nhất phải sau giờ sớm nhất");
        }
        return new TimeWindow(from, to);
    }

    /**
     * Gắn số ghế trống / đã bị giữ cho danh sách chuyến.
     *
     * <p>Ba câu truy vấn cho cả trang thay vì 3N. Xem
     * {@link TripSeatRepository#countByTripIdsGroupedByStatus}.
     */
    private List<TripSearchItemResponse> withSeatCounts(List<Trip> trips) {
        if (trips.isEmpty()) {
            return List.of();
        }
        List<Integer> tripIds = trips.stream().map(Trip::getId).toList();

        Map<Integer, Integer> available = new HashMap<>();
        Map<Integer, Integer> unavailable = new HashMap<>();
        for (Object[] row : tripSeatRepository.countByTripIdsGroupedByStatus(tripIds)) {
            Integer tripId = (Integer) row[0];
            SeatStatus status = (SeatStatus) row[1];
            int count = ((Number) row[2]).intValue();
            // Cộng dồn chứ không put: một chuyến có thể vừa có ghế BOOKED vừa có
            // ghế HOLDING, mỗi trạng thái một dòng trong kết quả group by. Nếu put
            // thì dòng sau đè dòng trước và số ghế không trống bị thiếu.
            if (status == SeatStatus.AVAILABLE) {
                available.merge(tripId, count, Integer::sum);
            } else {
                unavailable.merge(tripId, count, Integer::sum);
            }
        }
        Map<Integer, Integer> totals = countTotalsFor(tripIds);

        List<TripSearchItemResponse> items = new ArrayList<>(trips.size());
        for (Trip trip : trips) {
            int free = available.getOrDefault(trip.getId(), 0);
            int taken = unavailable.getOrDefault(trip.getId(), 0);
            // free + taken là TỔNG số ghế, không phải số ghế không trống. Cộng
            // nhầm ở đây thì mọi chuyến đều báo "hết chỗ" vì unavailable luôn bằng
            // totalSeats.
            items.add(TripSearchItemResponse.of(
                    TripResponse.from(trip, totals.get(trip.getId())), free, taken));
        }
        return items;
    }

    private Map<Integer, Integer> countTotalsFor(List<Integer> tripIds) {
        Map<Integer, Integer> totals = new HashMap<>();
        if (tripIds.isEmpty()) {
            return totals;
        }
        for (Object[] row : tripSeatRepository.countTotalByTripIds(tripIds)) {
            totals.put((Integer) row[0], ((Number) row[1]).intValue());
        }
        return totals;
    }

    private Trip requireById(Integer id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + id));
    }

    private record TimeWindow(LocalDateTime from, LocalDateTime to) {
    }
}