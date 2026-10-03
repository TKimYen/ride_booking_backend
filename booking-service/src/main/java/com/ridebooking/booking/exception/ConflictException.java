package com.ridebooking.booking.exception;

/**
 * Xung đột trạng thái nghiệp vụ — trả về 409 Conflict.
 *
 * <p>Khác {@code ResourceNotFoundException} (404: không tìm thấy) và
 * {@code BadRequestException} (400: dữ liệu sai cú pháp). 409 dành cho trường hợp
 * yêu cầu hợp lệ nhưng trạng thái hiện tại không cho phép:
 *
 * <ul>
 *   <li>tạo tuyến trùng tên với tuyến đã có</li>
 *   <li>đánh giá một chuyến đã đánh giá rồi (UNIQUE user_id + trip_id)</li>
 *   <li>giữ một ghế đang có người khác giữ (Giai đoạn 2)</li>
 *   <li>xoá một chuyến đã có đơn hàng</li>
 * </ul>
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}