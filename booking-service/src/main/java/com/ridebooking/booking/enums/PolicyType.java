package com.ridebooking.booking.enums;

/** Nhóm chính sách nhà xe ở tab Chính sách. Giá trị phải khớp CHECK {@code ck_company_policies_type}. */
public enum PolicyType {

	/** Huỷ vé, hoàn tiền. */
	CANCELLATION,

	/** Hành lý. */
	LUGGAGE,

	/** Trẻ em. */
	CHILDREN,

	/** Thú cưng. */
	PET,

	/** Giấy tờ tuỳ thân. */
	DOCUMENT,

	OTHER
}
