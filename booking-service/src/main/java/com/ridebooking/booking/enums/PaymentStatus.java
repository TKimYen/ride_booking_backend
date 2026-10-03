package com.ridebooking.booking.enums;

/**
 * Trạng thái một giao dịch thanh toán.
 *
 * <p>Khớp CHECK {@code payments.status} trong {@code database/03_booking_db.sql}.
 * Trong luồng demo, giao dịch được tạo và đánh {@code SUCCESS} ngay; khi nối cổng
 * thật (VNPAY), dòng được tạo ở {@code PENDING} rồi cập nhật theo callback.
 */
public enum PaymentStatus {

	/** Đã tạo giao dịch, chờ cổng thanh toán xác nhận. */
	PENDING,

	/** Cổng báo thành công. */
	SUCCESS,

	/** Cổng báo thất bại hoặc khách huỷ tại cổng. */
	FAILED
}
