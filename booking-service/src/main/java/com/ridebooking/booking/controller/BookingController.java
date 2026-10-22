package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.BookingLookupRequest;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đặt vé (Trang Chọn chỗ & Thanh toán, Trang Đơn hàng của tôi, Trang Tra cứu vé).
 *
 * <p>Hai kiểu chủ đơn (db.md mục B8):
 * <ul>
 *   <li>Tài khoản: người dùng lấy từ JWT, KHÔNG từ tham số — nếu không, người dùng
 *       này xem hoặc huỷ được đơn của người khác.</li>
 *   <li>Khách vãng lai: không có JWT, chứng minh quyền bằng header
 *       {@value #GUEST_TOKEN_HEADER} nhận được lúc tạo đơn hoặc tra cứu vé.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "4. Booking - Đặt vé", description = "Giữ ghế, thanh toán, huỷ, xem đơn, tra cứu vé")
public class BookingController {

    public static final String GUEST_TOKEN_HEADER = "X-Guest-Token";

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

                    **Không gửi JWT = khách vãng lai**: bắt buộc `contactEmail`; response có
                    `bookingCode` và `guestToken` (chỉ trả MỘT lần) — lưu lại và gửi trong
                    header `X-Guest-Token` cho các bước xem/thanh toán/huỷ.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã giữ ghế và tạo đơn"),
            @ApiResponse(responseCode = "400", description = "Thiếu thông tin, ghế không thuộc chuyến"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến hoặc ghế"),
            @ApiResponse(responseCode = "409", description = "Ghế đã có người giữ hoặc đã bán")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(@Valid @RequestBody HoldSeatsRequest request) {
        Integer userId = SecurityUtils.currentUserIdOrNull();
        return userId != null
                ? bookingService.holdSeats(userId, request)
                : bookingService.holdSeatsAsGuest(request);
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

    @Operation(summary = "Chi tiết một đơn",
            description = "Chủ đơn, Admin, hoặc khách vãng lai gửi header `X-Guest-Token` đúng.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Không có JWT và không có X-Guest-Token"),
            @ApiResponse(responseCode = "403", description = "Đơn không thuộc về người gọi / token sai"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn")})
    @GetMapping("/{id}")
    public BookingResponse getOne(@PathVariable Integer id,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestToken) {
        return bookingService.getOne(SecurityUtils.currentUserIdOrNull(), guestToken, id);
    }

    @Operation(summary = "Thanh toán đơn",
            description = """
                    Chuyển ghế HOLDING -> BOOKED và đơn -> PAID, ghi một dòng giao dịch.
                    Trong luồng demo chỉ cần gửi `gateway`; nếu có `responseCode` thì
                    chỉ `"00"` mới tính thành công.

                    Gọi lại trên đơn đã PAID trả về kết quả cũ (idempotent), nên callback
                    cổng thanh toán hoặc nút "Thử lại" không tạo giao dịch trùng.
                    Khách vãng lai gửi kèm header `X-Guest-Token`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thanh toán thành công hoặc đơn đã PAID"),
            @ApiResponse(responseCode = "409", description = "Đơn hết hạn, không còn PENDING, hoặc cổng báo lỗi")})
    @PostMapping("/{id}/pay")
    public BookingResponse pay(@PathVariable Integer id,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestToken,
            @Valid @RequestBody PaymentRequest request) {
        return bookingService.pay(SecurityUtils.currentUserIdOrNull(), guestToken, id, request);
    }

    @Operation(summary = "Huỷ đơn chưa thanh toán",
            description = "Trả ghế về AVAILABLE và đơn -> CANCELLED. Đơn đã PAID không huỷ "
                    + "trực tuyến được (cần luồng hoàn tiền) và trả về 409. Gọi lại trên đơn "
                    + "đã CANCELLED trả về kết quả cũ. Khách vãng lai gửi kèm header `X-Guest-Token`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã huỷ hoặc đơn đã CANCELLED trước đó"),
            @ApiResponse(responseCode = "409", description = "Đơn đã thanh toán hoặc đã hết hạn")})
    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Integer id,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestToken) {
        return bookingService.cancel(SecurityUtils.currentUserIdOrNull(), guestToken, id);
    }

    @Operation(summary = "Tra cứu vé (khách vãng lai)",
            description = """
                    Mã vé + số điện thoại liên hệ. Đơn của khách vãng lai trả kèm `guestToken`
                    MỚI (token cũ hết hiệu lực) để thanh toán/huỷ tiếp. Sai mã hoặc sai SĐT đều
                    trả cùng một thông báo 404.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy vé"),
            @ApiResponse(responseCode = "404", description = "Không có vé khớp mã và SĐT")})
    @PostMapping("/lookup")
    public BookingResponse lookup(@Valid @RequestBody BookingLookupRequest request) {
        return bookingService.lookup(request);
    }

    @Operation(summary = "Gắn vé đã đặt khi chưa đăng nhập vào tài khoản",
            description = "Cần JWT. Chứng minh bằng mã vé + SĐT; sau đó vé hiện ở \"Đơn hàng của tôi\".")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã gắn (hoặc vé đã thuộc chính tài khoản này)"),
            @ApiResponse(responseCode = "404", description = "Không có vé khớp mã và SĐT"),
            @ApiResponse(responseCode = "409", description = "Vé đã thuộc tài khoản khác")})
    @PostMapping("/claim")
    public BookingResponse claim(@Valid @RequestBody BookingLookupRequest request) {
        return bookingService.claim(SecurityUtils.currentUserIdOrThrow(), request);
    }
}
