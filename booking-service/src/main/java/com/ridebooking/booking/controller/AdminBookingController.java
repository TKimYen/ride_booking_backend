package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trang Quản lý Đơn hàng của Admin — danh sách vé/hành khách, lọc theo chuyến.
 *
 * <p>Chỉ đọc. Nghiệp vụ đổi trạng thái ghế thủ công (nếu có) sẽ bổ sung sau; hiện
 * tại mọi thay đổi trạng thái đều qua luồng khách đặt/huỷ và job hết hạn.
 */
@RestController
@RequestMapping("/api/admin/bookings")
@Tag(name = "9. Admin - Đơn hàng", description = "Danh sách đơn và hành khách theo chuyến")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Danh sách đơn hàng",
            description = """
                    Lọc theo `tripId` (danh sách khách của một chuyến) và/hoặc `status`.
                    Bỏ trống cả hai = mọi đơn. Sắp theo thời điểm tạo, mới nhất trước.
                    """)
    @GetMapping
    public PageResponse<BookingResponse> list(
            @Parameter(description = "id chuyến để xem danh sách khách") @RequestParam(required = false) Integer tripId,
            @Parameter(description = "Lọc trạng thái; lặp lại hoặc phân tách bằng dấu phẩy")
            @RequestParam(required = false) List<BookingStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "10") int size) {
        return bookingService.listForAdmin(tripId, status, page, size);
    }
}
