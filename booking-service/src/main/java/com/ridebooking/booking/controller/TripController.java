package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.TripResponse;
import com.ridebooking.booking.dto.TripSearchItemResponse;
import com.ridebooking.booking.dto.TripSearchRequest;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhóm Guest — API đọc chuyến xe.
 *
 * <p>Đây là API quan trọng nhất của Module Khám phá: Trang chủ và
 * Trang Danh sách kết quả đều dùng {@link #search}. Thêm/sửa/xoá nằm ở
 * {@code AdminTripController}.
 */
@RestController
@RequestMapping("/api/trips")
@Tag(name = "1. Catalog - Chuyến xe", description = "Tìm kiếm và xem chi tiết chuyến xe")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @Operation(summary = "Tìm chuyến xe (Trang Danh sách kết quả)",
            description = """
                    Mọi tham số đều tuỳ chọn; bỏ trống hết thì trả chuyến đang bán,
                    sắp theo giờ đi.

                    **Kết quả kèm số ghế.** Mỗi dòng có `availableSeats` để frontend
                    hiện "còn N chỗ". Ba câu truy vấn cho cả trang, không phải 3N.

                    **Lọc theo giờ.** `earliestDeparture` / `latestDeparture` mà không có
                    `travelDate` thì lấy hôm nay — giống cách người dùng hiểu khi gõ
                    "chuyến sau 6h chiều".

                    **Sắp xếp.** Chỉ nhận 5 giá trị trong allowlist; giá trị khác rơi về
                    mặc định thay vì lỗi 400, vì đây là tham số tiện ích của trang.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "400", description = "Khoảng giờ hoặc khoảng giá không hợp lệ")})
    @GetMapping
    public PageResponse<TripSearchItemResponse> search(
            @Parameter(description = "id địa điểm xuất phát") @RequestParam(required = false) Integer originId,
            @Parameter(description = "id địa điểm đến") @RequestParam(required = false) Integer destinationId,
            @Parameter(description = "id nhà xe") @RequestParam(required = false) Integer companyId,
            @Parameter(description = "Ngày đi, định dạng yyyy-MM-dd", example = "2026-10-05")
            @RequestParam(required = false) java.time.LocalDate travelDate,
            @Parameter(description = "Giờ khởi hành sớm nhất", example = "06:00:00")
            @RequestParam(required = false) java.time.LocalTime earliestDeparture,
            @Parameter(description = "Giờ khởi hành muộn nhất", example = "18:00:00")
            @RequestParam(required = false) java.time.LocalTime latestDeparture,
            @Parameter(description = "Giá thấp nhất") @RequestParam(required = false) java.math.BigDecimal minPrice,
            @Parameter(description = "Giá cao nhất") @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @Parameter(description = "Cách sắp xếp", example = "priceAsc")
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "10") int size) {

        TripSearchRequest request = new TripSearchRequest(
                originId, destinationId, companyId, travelDate,
                earliestDeparture, latestDeparture, minPrice, maxPrice, sort);
        return tripService.search(request, page, size);
    }

    @Operation(summary = "Chi tiết chuyến (Trang Thông tin chi tiết)",
            description = "Dùng cho phần đầu của trang: tuyến, nhà xe, giờ, giá, số ghế.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến")})
    @GetMapping("/{id}")
    public TripResponse getById(@PathVariable Integer id) {
        return tripService.getById(id);
    }

    @Operation(summary = "Các loại xe đang hỗ trợ",
            description = """
                    Frontend lấy danh sách này để dựng form "Thêm chuyến" và hiển thị
                    nhãn loại xe thay vì hard-code chuỗi.
                    """)
    @GetMapping("/bus-types")
    public List<BusTypeInfo> busTypes() {
        return List.of(BusType.values()).stream()
                .map(type -> new BusTypeInfo(
                        type.name(), type.displayName(), type.seatCount(), type.floors()))
                .toList();
    }

    /** Thông tin loại xe cho frontend, không lộ chi tiết sinh ghế. */
    public record BusTypeInfo(String code, String name, Integer seatCount, Integer floors) {
    }
}