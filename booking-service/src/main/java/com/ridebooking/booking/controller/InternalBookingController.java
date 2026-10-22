package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.InternalBookingResponse;
import com.ridebooking.booking.dto.InternalPaymentRequest;
import com.ridebooking.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nội bộ cho payment-service — KHÔNG dùng từ frontend.
 *
 * <p>Bảo vệ bằng header {@code X-Internal-Key} thay vì JWT vì webhook Stripe
 * chạy không có người dùng đăng nhập. Khoá đặt qua {@code app.internal.key} và
 * phải trùng với {@code app.clients.booking-service.internal-key} của
 * payment-service; Docker Compose cấp chung biến {@code INTERNAL_API_KEY}.
 *
 * <p>Controller này KHÔNG được route qua gateway: đường dẫn {@code /api/internal/**}
 * cố ý không nằm trong predicate nào của api-gateway để không lộ ra Internet.
 */
@RestController
@RequestMapping("/api/internal/bookings")
@SecurityRequirements
@Tag(name = "0. Internal (service ↔ service)",
        description = "Chỉ payment-service gọi được, cần header X-Internal-Key đúng")
public class InternalBookingController {

    private final BookingService bookingService;
    private final String internalKey;

    public InternalBookingController(BookingService bookingService,
                                     @Value("${app.internal.key}") String internalKey) {
        this.bookingService = bookingService;
        this.internalKey = internalKey;
    }

    @Operation(summary = "Lấy đơn để tạo phiên thanh toán",
            description = """
                    payment-service dùng số tiền/trạng thái/hạn giữ ghế để dựng Stripe Checkout Session.
                    Chuyển tiếp `X-Guest-Token` của khách vãng lai thì `guestAccess` cho biết token có khớp đơn không.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "403", description = "Thiếu hoặc sai X-Internal-Key"),
            @ApiResponse(responseCode = "404", description = "Không có đơn")})
    @GetMapping("/{bookingId}")
    public ResponseEntity<InternalBookingResponse> getForPayment(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            @PathVariable Integer bookingId) {
        requireKey(key);
        return ResponseEntity.ok(bookingService.getForPayment(bookingId, guestToken));
    }

    @Operation(summary = "Xác nhận đã thu tiền (idempotent)",
            description = """
                    Gọi bởi payment-service sau khi webhook Stripe báo thu tiền thành công.
                    Ghế HOLDING -> BOOKED, đơn -> PAID, ghi một dòng payments SUCCESS.

                    Gọi lại nhiều lần cho cùng giao dịch trả về kết quả cũ, không tạo
                    giao dịch thứ hai. Nếu ghế đã bị trả về AVAILABLE (đơn hết hạn),
                    trả 409 để payment-service hoàn tiền.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã xác nhận (hoặc đã xác nhận trước đó)"),
            @ApiResponse(responseCode = "403", description = "Sai X-Internal-Key"),
            @ApiResponse(responseCode = "404", description = "Không có đơn"),
            @ApiResponse(responseCode = "409", description = "Đơn không còn ở trạng thái chờ thanh toán")})
    @PostMapping("/{bookingId}/confirm-payment")
    public ResponseEntity<BookingResponse> confirmPayment(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @PathVariable Integer bookingId,
            @Valid @RequestBody InternalPaymentRequest request) {
        requireKey(key);
        return ResponseEntity.ok(bookingService.confirmPayment(bookingId, request));
    }

    private void requireKey(String key) {
        if (key == null || !internalKey.equals(key)) {
            throw new AccessDeniedException("Internal key không hợp lệ");
        }
    }
}
