package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.stats.DailyRevenueResponse;
import com.ridebooking.booking.dto.stats.StatsOverviewResponse;
import com.ridebooking.booking.dto.stats.StatusCountResponse;
import com.ridebooking.booking.dto.stats.TopRouteResponse;
import com.ridebooking.booking.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard Admin [3.5]. Mọi API nhận {@code from}/{@code to} (yyyy-MM-dd, trọn ngày
 * ở cả hai đầu); bỏ trống = 30 ngày gần nhất. {@code companyId} tuỳ chọn để lọc theo nhà xe. Tối đa 366 ngày; from &gt; to → 400.
 */
@RestController
@RequestMapping("/api/admin/stats")
@Tag(name = "9. Admin - Thống kê", description = "Số liệu cho Dashboard")
public class AdminStatsController {

    private final StatsService statsService;

    public AdminStatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "Tổng quan", description = "Doanh thu, số đơn/vé đã bán, đơn mới, đơn huỷ, chuyến sắp chạy.")
    @GetMapping("/overview")
    public StatsOverviewResponse overview(
            @Parameter(example = "2026-10-11") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(example = "2026-11-09") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Lọc theo nhà xe; bỏ trống = toàn hệ thống")
            @RequestParam(required = false) Integer companyId) {
        return statsService.overview(from, to, companyId);
    }

    @Operation(summary = "Doanh thu theo ngày", description = "Đủ mọi ngày trong khoảng; ngày không có đơn = 0.")
    @GetMapping("/revenue")
    public List<DailyRevenueResponse> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Lọc theo nhà xe; bỏ trống = toàn hệ thống")
            @RequestParam(required = false) Integer companyId) {
        return statsService.dailyRevenue(from, to, companyId);
    }

    @Operation(summary = "Số đơn theo trạng thái", description = "Đơn tạo trong khoảng; luôn đủ 5 trạng thái.")
    @GetMapping("/bookings-by-status")
    public List<StatusCountResponse> bookingsByStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Lọc theo nhà xe; bỏ trống = toàn hệ thống")
            @RequestParam(required = false) Integer companyId) {
        return statsService.bookingsByStatus(from, to, companyId);
    }

    @Operation(summary = "Tuyến bán chạy", description = "Xếp theo doanh thu giảm dần. `limit` 1–20, mặc định 5.")
    @GetMapping("/top-routes")
    public List<TopRouteResponse> topRoutes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Lọc theo nhà xe; bỏ trống = toàn hệ thống")
            @RequestParam(required = false) Integer companyId,
            @RequestParam(defaultValue = "5") int limit) {
        return statsService.topRoutes(from, to, companyId, limit);
    }
}
