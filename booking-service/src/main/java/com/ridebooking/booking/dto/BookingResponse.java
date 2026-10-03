package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.entity.BookingDetail;
import com.ridebooking.booking.entity.Payment;
import com.ridebooking.booking.enums.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Thông tin một đơn đặt vé, dùng cho cả trang chi tiết và danh sách "Đơn hàng của tôi". */
@Schema(description = "Đơn đặt vé")
public record BookingResponse(

        @Schema(example = "12") Integer id,
        @Schema(example = "7") Integer userId,
        BookingTripResponse trip,
        @Schema(description = "Điểm đón đã chọn; null nếu không chọn") StopPointResponse pickupPoint,
        @Schema(description = "Điểm trả đã chọn; null nếu không chọn") StopPointResponse dropoffPoint,
        @Schema(example = "Nguyễn Văn A") String contactName,
        @Schema(example = "0901234567") String contactPhone,
        @Schema(example = "a@example.com") String contactEmail,
        @Schema(example = "500000.00") BigDecimal totalPrice,
        @Schema(description = "PENDING | PAID | CANCELLED | COMPLETED | EXPIRED", example = "PENDING")
        String status,
        @Schema(example = "2026-10-02T10:00:00") LocalDateTime createdAt,
        @Schema(description = "Hạn thanh toán; chỉ có khi PENDING", example = "2026-10-02T10:10:00")
        LocalDateTime expiresAt,
        @Schema(description = "Số giây còn lại để thanh toán; null khi đơn không ở PENDING", example = "412")
        Long holdSecondsRemaining,
        List<BookingSeatResponse> seats,
        @Schema(description = "Giao dịch thành công gần nhất; null khi chưa thanh toán") PaymentResponse payment) {

    public static BookingResponse from(Booking booking, List<BookingDetail> seats, Payment payment,
            LocalDateTime now) {
        Long holdSeconds = null;
        if (booking.getStatus() == BookingStatus.PENDING && booking.getExpiresAt() != null) {
            holdSeconds = Math.max(0, Duration.between(now, booking.getExpiresAt()).getSeconds());
        }
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                BookingTripResponse.from(booking.getTrip()),
                booking.getPickupPoint() == null ? null : StopPointResponse.from(booking.getPickupPoint()),
                booking.getDropoffPoint() == null ? null : StopPointResponse.from(booking.getDropoffPoint()),
                booking.getContactName(),
                booking.getContactPhone(),
                booking.getContactEmail(),
                booking.getTotalPrice(),
                booking.getStatus().name(),
                booking.getCreatedAt(),
                booking.getExpiresAt(),
                holdSeconds,
                seats.stream().map(BookingSeatResponse::from).toList(),
                payment == null ? null : PaymentResponse.from(payment));
    }
}
