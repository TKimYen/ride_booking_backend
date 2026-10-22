package com.ridebooking.booking.enums;

/** Kiểu giảm giá của khuyến mãi. Giá trị phải khớp CHECK {@code ck_promotions_discount}. */
public enum DiscountType {

	/** Giảm theo phần trăm tổng tiền, có thể giới hạn bởi {@code max_discount}. */
	PERCENT,

	/** Giảm một số tiền cố định. */
	FIXED
}
