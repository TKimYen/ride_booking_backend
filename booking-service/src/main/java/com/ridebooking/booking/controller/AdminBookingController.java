package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.AdminBookingCancellationResponse;
import com.ridebooking.booking.dto.AdminCancelBookingRequest;
import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trang Quản lý Đơn hàng của Admin [4.5]: tìm đơn theo từ khoá/khoảng ngày, xem
 * chi tiết, huỷ đơn kèm lý do.
 */
@RestController
@RequestMapping("/api/admin/bookings")
@Tag(name = "9. Admin - Đơn hàng", description = "Tìm, xem và huỷ đơn")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Danh sách đơn hàng",
            description = """
                    Mọi tham số đều tuỳ chọn, bỏ trống = mọi đơn. Sắp theo thời điểm tạo, mới nhất trước.
                    - `keyword`: khớp một phần mã vé, tên, SĐT hoặc email liên hệ (không phân biệt hoa thường).
                    - `from`/`to`: ngày tạo đơn (yyyy-MM-dd), tính trọn ngày ở cả hai đầu. `from` > `to` → 400.
                    """)
    @GetMapping
    public PageResponse<BookingResponse> list(
            @Parameter(description = "id chuyến để xem danh sách khách") @RequestParam(required = false) Integer tripId,
            @Parameter(description = "Lọc trạng thái; lặp lại hoặc phân tách bằng dấu phẩy")
            @RequestParam(required = false) List<BookingStatus> status,
            @Parameter(description = "Mã vé, tên, SĐT hoặc email", example = "0901")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Từ ngày tạo đơn", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Đến ngày tạo đơn", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "10") int size) {
        return bookingService.searchForAdmin(tripId, status, keyword, from, to, page, size);
    }

    @Operation(summary = "Chi tiết một đơn")
    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Integer id) {
        return bookingService.getOneForAdmin(id);
    }

    @Operation(summary = "Admin huỷ đơn",
            description = """
                    Bắt buộc `reason` (thiếu/trống → 400). Huỷ được đơn PENDING còn hạn hoặc PAID của chuyến
                    chưa khởi hành; ghế được trả lại. Đơn COMPLETED/EXPIRED hoặc chuyến đã chạy → 409.
                    Gọi lại trên đơn đã huỷ không lỗi. Nếu đơn đã thu tiền, `refund` có thông tin cần hoàn —
                    hệ thống chưa tự hoàn tiền.
                    """)
    @PostMapping("/{id}/cancel")
    public AdminBookingCancellationResponse cancel(@PathVariable Integer id,
                                                   @Valid @RequestBody AdminCancelBookingRequest request) {
        return bookingService.cancelByAdmin(id, request.reason());
    }
}
