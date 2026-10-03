package com.ridebooking.booking.exception;

/**
 * Dữ liệu yêu cầu sai về mặt nghiệp vụ nhưng đúng cú pháp — trả về 400.
 *
 * <p>Khác {@code ConflictException} (409: trạng thái hiện tại không cho phép) và
 * {@code ResourceNotFoundException} (404). Ví dụ:
 *
 * <ul>
 *   <li>ghế gửi lên không thuộc chuyến đang đặt</li>
 *   <li>điểm trả khách không thuộc chuyến</li>
 *   <li>đặt quá số ghế tối đa cho một đơn</li>
 * </ul>
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
