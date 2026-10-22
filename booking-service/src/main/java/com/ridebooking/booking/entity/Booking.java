package com.ridebooking.booking.entity;

import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.util.BookingCodeGenerator;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng {@code bookings} — một đơn đặt vé của một người dùng cho một chuyến.
 *
 * <p>Một đơn gồm 1..N ghế, mỗi ghế một dòng {@link BookingDetail}. Giá chốt tại
 * thời điểm đặt nằm ở {@code booking_details.price}, nên nhà xe đổi giá vé sau
 * này không làm sai hoá đơn cũ.
 *
 * <p>{@code userId} là id bên auth_db, cố tình KHÔNG có khoá ngoại — hai database
 * tách rời, xem giải thích trong {@code database/03_booking_db.sql}. {@code userId}
 * NULL là đơn của khách vãng lai; khi đó {@code guestTokenHash} chứng minh quyền
 * với đơn (db.md mục B8).
 *
 * <p>{@code bookingCode} là mã vé hiển thị cho khách, sinh tự động khi lưu.
 *
 * <p>Đơn {@code PENDING} có {@code expiresAt} = mốc giữ ghế (mặc định 10 phút).
 * Job quét {@code status = PENDING AND expiresAt < now} để trả ghế và đánh
 * {@code EXPIRED}.
 */
@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(
		name = "uk_bookings_code", columnNames = "booking_code"))
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	/** Id người dùng bên auth_db (không FK). NULL = khách vãng lai. */
	@Column(name = "user_id")
	private Integer userId;

	/**
	 * Mã vé cho khách tra cứu. DB đặt NOT NULL; ở entity để trống được vì
	 * {@code ddl-auto=update} không thêm được cột NOT NULL vào bảng đã có dữ liệu.
	 */
	@Column(name = "booking_code", length = 12)
	private String bookingCode;

	/** SHA-256 hex của token khách vãng lai; NULL với đơn của tài khoản. */
	@Column(name = "guest_token_hash", length = 64)
	private String guestTokenHash;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false)
	private Trip trip;

	/** Điểm đón khách chọn; có thể null. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pickup_point_id")
	private StopPoint pickupPoint;

	/** Điểm trả khách chọn; có thể null. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "dropoff_point_id")
	private StopPoint dropoffPoint;

	@Column(name = "contact_name", length = 255)
	private String contactName;

	@Column(name = "contact_phone", length = 20)
	private String contactPhone;

	@Column(name = "contact_email", length = 255)
	private String contactEmail;

	/** Mã giảm giá đã áp; NULL khi không dùng mã. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "promotion_id")
	private Promotion promotion;

	/** Tổng giá ghế trước giảm. */
	@Column(name = "subtotal_price", precision = 12, scale = 2)
	private BigDecimal subtotalPrice;

	@Column(name = "discount_amount", precision = 12, scale = 2)
	private BigDecimal discountAmount = BigDecimal.ZERO;

	/** Số tiền phải trả = subtotalPrice - discountAmount. */
	@Column(name = "total_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalPrice = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private BookingStatus status = BookingStatus.PENDING;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/** Mốc hết hạn thanh toán; chỉ có ý nghĩa với đơn PENDING. */
	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "cancel_reason", length = 255)
	private String cancelReason;

	@PrePersist
	void onCreate() {
		if (bookingCode == null) {
			bookingCode = BookingCodeGenerator.next();
		}
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (status == null) {
			status = BookingStatus.PENDING;
		}
		if (totalPrice == null) {
			totalPrice = BigDecimal.ZERO;
		}
		if (discountAmount == null) {
			discountAmount = BigDecimal.ZERO;
		}
		if (subtotalPrice == null) {
			subtotalPrice = totalPrice.add(discountAmount);
		}
	}

	/** Đơn của khách vãng lai (không gắn tài khoản). */
	public boolean isGuest() {
		return userId == null;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getUserId() {
		return userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	public Trip getTrip() {
		return trip;
	}

	public void setTrip(Trip trip) {
		this.trip = trip;
	}

	public StopPoint getPickupPoint() {
		return pickupPoint;
	}

	public void setPickupPoint(StopPoint pickupPoint) {
		this.pickupPoint = pickupPoint;
	}

	public StopPoint getDropoffPoint() {
		return dropoffPoint;
	}

	public void setDropoffPoint(StopPoint dropoffPoint) {
		this.dropoffPoint = dropoffPoint;
	}

	public String getContactName() {
		return contactName;
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public void setContactPhone(String contactPhone) {
		this.contactPhone = contactPhone;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public void setContactEmail(String contactEmail) {
		this.contactEmail = contactEmail;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public void setTotalPrice(BigDecimal totalPrice) {
		this.totalPrice = totalPrice;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public void setStatus(BookingStatus status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(LocalDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public String getBookingCode() {
		return bookingCode;
	}

	public void setBookingCode(String bookingCode) {
		this.bookingCode = bookingCode;
	}

	public String getGuestTokenHash() {
		return guestTokenHash;
	}

	public void setGuestTokenHash(String guestTokenHash) {
		this.guestTokenHash = guestTokenHash;
	}

	public Promotion getPromotion() {
		return promotion;
	}

	public void setPromotion(Promotion promotion) {
		this.promotion = promotion;
	}

	public BigDecimal getSubtotalPrice() {
		return subtotalPrice;
	}

	public void setSubtotalPrice(BigDecimal subtotalPrice) {
		this.subtotalPrice = subtotalPrice;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public void setDiscountAmount(BigDecimal discountAmount) {
		this.discountAmount = discountAmount;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(LocalDateTime paidAt) {
		this.paidAt = paidAt;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public void setCancelledAt(LocalDateTime cancelledAt) {
		this.cancelledAt = cancelledAt;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public void setCancelReason(String cancelReason) {
		this.cancelReason = cancelReason;
	}
}
