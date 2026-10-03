package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Payment;
import com.ridebooking.booking.enums.PaymentGateway;
import com.ridebooking.booking.enums.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

	/** Lịch sử giao dịch của một đơn, mới nhất trước. */
	List<Payment> findByBookingIdOrderByCreatedAtDesc(Integer bookingId);

	/** Giao dịch thành công gần nhất, dùng để trả kết quả thanh toán idempotent. */
	Optional<Payment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(Integer bookingId, PaymentStatus status);

	/** Giao dịch thành công của NHIỀU đơn trong một truy vấn, tránh N+1 ở trang danh sách. */
	List<Payment> findByBookingIdInAndStatusOrderByCreatedAtDesc(Collection<Integer> bookingIds,
			PaymentStatus status);

	/** Chống ghi trùng theo UNIQUE (payment_gateway, transaction_no). */
	Optional<Payment> findByPaymentGatewayAndTransactionNo(PaymentGateway paymentGateway, String transactionNo);
}
