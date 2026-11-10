package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.stats.DailyRevenueResponse;
import com.ridebooking.booking.dto.stats.StatsOverviewResponse;
import com.ridebooking.booking.dto.stats.StatusCountResponse;
import com.ridebooking.booking.dto.stats.TopRouteResponse;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.BadRequestException;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.TripRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Số liệu cho Dashboard Admin [3.5].
 *
 * <p>Doanh thu = tổng {@code totalPrice} của đơn PAID và COMPLETED, tính theo ngày
 * thanh toán. Đơn đã thu tiền rồi bị huỷ (huỷ chuyến/Admin huỷ) chuyển sang
 * CANCELLED nên tự rơi khỏi doanh thu, khớp với số tiền thực giữ lại sau khi hoàn.
 *
 * <p>Mọi số liệu lọc được theo nhà xe ({@code companyId} null = toàn hệ thống).
 *
 * <p>Khoảng ngày tính trọn ngày ở cả hai đầu; mặc định 30 ngày gần nhất, tối đa
 * 366 ngày để biểu đồ theo ngày không quá dài.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    static final int DEFAULT_RANGE_DAYS = 30;
    static final int MAX_RANGE_DAYS = 366;
    private static final List<BookingStatus> PAID_STATUSES = List.of(BookingStatus.PAID, BookingStatus.COMPLETED);

    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;

    public StatsService(BookingRepository bookingRepository, TripRepository tripRepository) {
        this.bookingRepository = bookingRepository;
        this.tripRepository = tripRepository;
    }

    public StatsOverviewResponse overview(LocalDate from, LocalDate to, Integer companyId) {
        Range range = Range.of(from, to);
        Accumulator total = new Accumulator();
        for (Object[] row : bookingRepository.paidRowsBetween(PAID_STATUSES, range.start(), range.end(), companyId)) {
            total.add(row);
        }
        return new StatsOverviewResponse(
                range.from(),
                range.to(),
                total.revenue,
                total.bookings,
                total.tickets,
                bookingRepository.countCreatedBetween(range.start(), range.end(), companyId),
                bookingRepository.countCancelledBetween(BookingStatus.CANCELLED, range.start(), range.end(), companyId),
                tripRepository.countUpcoming(TripStatus.ACTIVE, LocalDateTime.now(), companyId));
    }

    /** Doanh thu từng ngày trong khoảng, đủ mọi ngày (ngày trống = 0) để vẽ biểu đồ liền mạch. */
    public List<DailyRevenueResponse> dailyRevenue(LocalDate from, LocalDate to, Integer companyId) {
        Range range = Range.of(from, to);
        Map<LocalDate, Accumulator> byDay = new LinkedHashMap<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            byDay.put(day, new Accumulator());
        }
        for (Object[] row : bookingRepository.paidRowsBetween(PAID_STATUSES, range.start(), range.end(), companyId)) {
            Accumulator acc = byDay.get(((LocalDateTime) row[0]).toLocalDate());
            if (acc != null) {
                acc.add(row);
            }
        }
        List<DailyRevenueResponse> result = new ArrayList<>(byDay.size());
        byDay.forEach((day, acc) -> result.add(new DailyRevenueResponse(day, acc.revenue, acc.bookings, acc.tickets)));
        return result;
    }

    /** Đơn tạo trong khoảng, đếm theo trạng thái; luôn đủ 5 trạng thái theo thứ tự enum. */
    public List<StatusCountResponse> bookingsByStatus(LocalDate from, LocalDate to, Integer companyId) {
        Range range = Range.of(from, to);
        Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : bookingRepository.countByStatusCreatedBetween(range.start(), range.end(), companyId)) {
            counts.put((BookingStatus) row[0], ((Number) row[1]).longValue());
        }
        return counts.entrySet().stream()
                .map(e -> new StatusCountResponse(e.getKey().name(), e.getValue()))
                .toList();
    }

    /** Tuyến bán chạy theo doanh thu; {@code limit} bị kẹp trong 1–20. */
    public List<TopRouteResponse> topRoutes(LocalDate from, LocalDate to, Integer companyId, int limit) {
        Range range = Range.of(from, to);
        int size = Math.max(1, Math.min(limit, 20));
        List<Object[]> rows = bookingRepository.topRoutesBetween(
                PAID_STATUSES, range.start(), range.end(), companyId, PageRequest.of(0, size));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Integer> routeIds = rows.stream().map(row -> (Integer) row[0]).toList();
        Map<Integer, Long> tickets = new HashMap<>();
        for (Object[] row : bookingRepository.ticketsByRouteBetween(
                PAID_STATUSES, range.start(), range.end(), routeIds, companyId)) {
            tickets.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        return rows.stream()
                .map(row -> new TopRouteResponse(
                        (Integer) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        ((Number) row[4]).longValue(),
                        tickets.getOrDefault((Integer) row[0], 0L),
                        (BigDecimal) row[5]))
                .toList();
    }

    /** Cộng dồn các dòng [paidAt, totalPrice, soGhe]. */
    private static final class Accumulator {
        BigDecimal revenue = BigDecimal.ZERO;
        long bookings;
        long tickets;

        void add(Object[] row) {
            revenue = revenue.add((BigDecimal) row[1]);
            bookings++;
            tickets += ((Number) row[2]).longValue();
        }
    }

    /** Khoảng ngày đã chuẩn hoá; {@code start}/{@code end} là [from 00:00, to+1 00:00). */
    record Range(LocalDate from, LocalDate to) {

        static Range of(LocalDate from, LocalDate to) {
            LocalDate end = to != null ? to : LocalDate.now();
            LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1);
            if (start.isAfter(end)) {
                throw new BadRequestException("Ngày bắt đầu (from) phải trước hoặc bằng ngày kết thúc (to)");
            }
            if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
                throw new BadRequestException("Khoảng thống kê tối đa " + MAX_RANGE_DAYS + " ngày");
            }
            return new Range(start, end);
        }

        LocalDateTime start() {
            return from.atStartOfDay();
        }

        LocalDateTime end() {
            return to.plusDays(1).atStartOfDay();
        }
    }
}
