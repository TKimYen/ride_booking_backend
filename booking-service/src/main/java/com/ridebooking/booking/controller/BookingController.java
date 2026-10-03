package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.security.SecurityUtils;
import com.ridebooking.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đặt vé của khách đã đăng nhập (Trang Chọn chỗ & Thanh toán và Trang Đơn
 * hàng của tôi).
 *
 * <p>Mọi endpoint lấy người dùng từ JWT, KHÔNG từ tham số — nếu không, người dùng
 * này xem hoặc huỷ được đơn của người khác.
 */
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "4. Booking - Đặt vé", description = "Giữ ghế, thanh toán, huỷ và xem đơn của tôi")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Giữ ghế và tạo đơn (Trang Chọn chỗ & Thanh toán)",
            description = """
                    Giữ các ghế trong `seatIds` và tạo đơn PENDING trong cùng một
                    transaction. Ghế được khoá bằng `SELECT ... FOR UPDATE`, nên hai
                    người giành cùng một ghế chỉ có một người thắng; người sau nhận
                    **409** kèm tên ghế đã bị chiếm.

                    Ghế đang giữ nhưng **đã quá hạn** được coi là còn trống và giành
                    lại được ngay, không phải chờ job quét.

                    Đơn có `expiresAt` = hiện tại + `app.booking.seat-hold-minutes`
                    (mặc định 10 phút) và `holdSecondsRemaining` để frontend đếm ngược.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã giữ ghế và tạo đơn"),
            @ApiResponse(responseCode = "400", description = "Thiếu thông tin, ghế không thuộc chuyến"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến hoặc ghế"),
            @ApiResponse(responseCode = "409", description = "Ghế đã có người giữ hoặc đã bán")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(@Valid @RequestBody HoldSeatsRequest request) {
        return bookingService.holdSeats(SecurityUtils.currentUserIdOrThrow(), request);
    }

    @Operation(summary = "Đơn hàng của tôi",
            description = """
                    Lọc theo một hoặc nhiều trạng thái, ví dụ `?status=PENDING,PAID`.
                    Bỏ trống `status` = tất cả. Sắp theo thời điểm tạo, mới nhất trước.

                    Gợi ý tab: **Hiện tại** = `PENDING,PAID`; **Đã đi** = `COMPLETED`;
                    **Đã huỷ** = `CANCELLED,EXPIRED`.
                    """)
    @GetMapping
    public PageResponse<BookingResponse> listMine(
            @Parameter(description = "Lọc trạng thái; lặp lại hoặc phân tách bằng dấu phẩy")
            @RequestParam(required = false) List<BookingStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "10") int size) {
        return bookingService.listMine(SecurityUtils.currentUserIdOrThrow(), status, page, size);
    }

    @Operation(summary = "Chi tiết một đơn của tôi")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "403", description = "Đơn không thuộc về người đang đăng nhập"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn")})
    @GetMapping("/{id}")
    public BookingResponse getOne(@PathVariable Integer id) {
        return bookingService.getOne(SecurityUtils.currentUserIdOrThrow(), id);
    }

    @Operation(summary = "Thanh toán đơn",
            description = """
                    Chuyển ghế HOLDING -> BOOKED và đơn -> PAID, ghi một dòng giao dịch.
                    Trong luồng demo chỉ cần gửi `gateway`; nếu có `responseCode` thì
                    chỉ `"00"` mới tính thành công.

                    Gọi lại trên đơn đã PAID trả về kết quả cũ (idempotent), nên callback
                    cổng thanh toán hoặc nút "Thử lại" không tạo giao dịch trùng.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thanh toán thành công hoặc đơn đã PAID"),
            @ApiResponse(responseCode = "409", description = "Đơn hết hạn, không còn PENDING, hoặc cổng báo lỗi")})
    @PostMapping("/{id}/pay")
    public BookingResponse pay(@PathVariable Integer id, @Valid @RequestBody PaymentRequest request) {
        return bookingService.pay(SecurityUtils.currentUserIdOrThrow(), id, request);
    }

    @Operation(summary = "Huỷ đơn chưa thanh toán",
            description = "Trả ghế về AVAILABLE và đơn -> CANCELLED. Đơn đã PAID không huỷ "
                    + "trực tuyến được (cần luồng hoàn tiền) và trả về 409. Gọi lại trên đơn "
                    + "đã CANCELLED trả về kết quả cũ.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã huỷ hoặc đơn đã CANCELLED trước đó"),
            @ApiResponse(responseCode = "409", description = "Đơn đã thanh toán hoặc đã hết hạn")})
    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Integer id) {
        return bookingService.cancel(SecurityUtils.currentUserIdOrThrow(), id);
    }
}
