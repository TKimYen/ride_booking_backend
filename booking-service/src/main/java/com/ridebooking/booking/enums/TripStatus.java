package com.ridebooking.booking.enums;

/** Trạng thái chuyến xe. Giá trị phải khớp CHECK {@code ck_trips_status}. */
public enum TripStatus {

	/** Đang bán vé. */
	ACTIVE,

	/** Nhà xe huỷ chuyến. Ghế được trả về AVAILABLE. */
	CANCELLED,

	/** Đã khởi hành. Chuyến này mới được đánh giá (Trang Đánh giá). */
	COMPLETED
}
