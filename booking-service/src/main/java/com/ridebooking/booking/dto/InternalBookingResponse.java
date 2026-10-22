package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dữ liệu booking-service trả cho payment-service khi tạo phiên thanh toán.
 *
 * <p>Cố ý tối thiểu: chỉ những gì cổng cần để dựng giao dịch (mã đơn, số tiền,
 * trạng thái, hạn giữ ghế). Không lộ tên/điện thoại/email khách hay danh sách
 * ghế — payment-service không cần và càng ít dữ liệu rời service càng tốt.
 *
 * <p>{@code payable} gộp sẵn điều kiện "đang PENDING và chưa hết hạn" để
 * payment-service khỏi tự suy diễn từ {@code status} + {@code expiresAt} và lệch
 * logic với booking-service.
 *
 * <p>{@code guestAccess} = đơn của khách vãng lai VÀ header {@code X-Guest-Token}
 * payment-service chuyển tiếp khớp hash. booking-service tự so token để hash không
 * bao giờ rời service.
 */
public record InternalBookingResponse(
        Integer id,
        Integer userId,
        BigDecimal totalPrice,
        String currency,
        BookingStatus status,
        LocalDateTime expiresAt,
        boolean payable,
        boolean guestAccess) {

    public static InternalBookingResponse from(Booking booking, boolean guestAccess) {
        boolean payable = booking.getStatus() == BookingStatus.PENDING
                && booking.getExpiresAt() != null
                && booking.getExpiresAt().isAfter(LocalDateTime.now());
        return new InternalBookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getTotalPrice(),
                "VND",
                booking.getStatus(),
                booking.getExpiresAt(),
                payable,
                guestAccess);
    }
}
