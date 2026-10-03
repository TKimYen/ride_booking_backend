package com.ridebooking.booking.enums;

/**
 * Trạng thái một ghế trong sơ đồ.
 *
 * <p>Vòng đời: {@code AVAILABLE -> HOLDING -> BOOKED}, và {@code HOLDING ->
 * AVAILABLE} khi hết 10 phút. Chuyển trạng thái luôn đi qua khoá dòng
 * {@code SELECT ... FOR UPDATE} trên chính dòng ghế đó (Giai đoạn 2).
 */
public enum SeatStatus {

	/** Còn trống. */
	AVAILABLE,

	/** Khách đang giữ, chờ thanh toán. Kèm {@code holdingUntil}. */
	HOLDING,

	/** Đã bán. */
	BOOKED
}
