package com.ridebooking.booking.enums;

/**
 * Cổng thanh toán được hỗ trợ.
 *
 * <p>Khớp CHECK {@code payments.payment_gateway}. Luồng thật chạy ở
 * payment-service: nó gọi API nội bộ của booking-service để ghi giao dịch với
 * đúng mã trong enum này. {@code STRIPE} là cổng đang dùng; VNPAY/MOMO/ZALOPAY
 * giữ lại cho luồng demo cũ và các cổng nội địa sẽ nối sau.
 */
public enum PaymentGateway {

	STRIPE,
	VNPAY,
	MOMO,
	ZALOPAY
}
