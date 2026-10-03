package com.ridebooking.booking.entity;

import com.ridebooking.booking.enums.PaymentGateway;
import com.ridebooking.booking.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng {@code payments} — lịch sử một giao dịch thanh toán của một đơn.
 *
 * <p>Bảng nằm trong {@code booking_db} dù tiền đi qua cổng bên ngoài, để việc
 * "đánh dấu ghế BOOKED" và "ghi nhận giao dịch" nằm cùng một transaction. Nếu
 * tách database, hai việc này có thể lệch nhau khi một bên lỗi.
 *
 * <p>{@code (payment_gateway, transaction_no)} là UNIQUE — khoá chống ghi trùng
 * khi cổng báo thành công hai lần cho cùng một mã giao dịch.
 */
@Entity
@Table(name = "payments")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_gateway", nullable = false, length = 20)
	private PaymentGateway paymentGateway;

	/** Mã giao dịch phía cổng; null khi giao dịch chưa được cổng trả mã. */
	@Column(name = "transaction_no", length = 100)
	private String transactionNo;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "bank_code", length = 50)
	private String bankCode;

	@Column(name = "response_code", length = 20)
	private String responseCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PaymentStatus status = PaymentStatus.PENDING;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (status == null) {
			status = PaymentStatus.PENDING;
		}
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Booking getBooking() {
		return booking;
	}

	public void setBooking(Booking booking) {
		this.booking = booking;
	}

	public PaymentGateway getPaymentGateway() {
		return paymentGateway;
	}

	public void setPaymentGateway(PaymentGateway paymentGateway) {
		this.paymentGateway = paymentGateway;
	}

	public String getTransactionNo() {
		return transactionNo;
	}

	public void setTransactionNo(String transactionNo) {
		this.transactionNo = transactionNo;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getBankCode() {
		return bankCode;
	}

	public void setBankCode(String bankCode) {
		this.bankCode = bankCode;
	}

	public String getResponseCode() {
		return responseCode;
	}

	public void setResponseCode(String responseCode) {
		this.responseCode = responseCode;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public void setStatus(PaymentStatus status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
