package com.ridebooking.booking.enums;

/**
 * Trạng thái một đơn đặt vé.
 *
 * <p>Vòng đời:
 * <pre>
 *   PENDING ──pay──▶ PAID ──chuyến chạy xong──▶ COMPLETED
 *      │
 *      ├──cancel──▶ CANCELLED   (khách chủ động)
 *      └──hết 10 phút──▶ EXPIRED (khách bỏ quên)
 * </pre>
 *
 * <p>Phân biệt {@code CANCELLED} với {@code EXPIRED} là cố ý: cả hai đều trả ghế
 * về {@code AVAILABLE}, nhưng với khách đây là hai câu chuyện khác nhau — "tôi đã
 * huỷ" so với "tôi quên thanh toán". Trang Đơn hàng của tôi hiển thị khác nhau.
 *
 * <p>Giá trị phải khớp CHECK trong {@code database/03_booking_db.sql}.
 */
public enum BookingStatus {

	/** Đã giữ ghế, chờ thanh toán. Có {@code expiresAt}. */
	PENDING,

	/** Đã thanh toán, ghế đã chuyển sang {@code BOOKED}. */
	PAID,

	/** Khách chủ động huỷ trước khi thanh toán. Ghế đã trả lại. */
	CANCELLED,

	/** Chuyến đã hoàn thành (job/Admin đánh dấu sau khi tới giờ đến). */
	COMPLETED,

	/** Quá hạn thanh toán. Ghế đã trả lại tự động. */
	EXPIRED
}
