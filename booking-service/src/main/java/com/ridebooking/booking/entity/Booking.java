package com.ridebooking.booking.entity;

import com.ridebooking.booking.enums.BookingStatus;
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
 * Bảng {@code bookings} — một đơn đặt vé của một người dùng cho một chuyến.
 *
 * <p>Một đơn gồm 1..N ghế, mỗi ghế một dòng {@link BookingDetail}. Giá chốt tại
 * thời điểm đặt nằm ở {@code booking_details.price}, nên nhà xe đổi giá vé sau
 * này không làm sai hoá đơn cũ.
 *
 * <p>{@code userId} là id bên auth_db, cố tình KHÔNG có khoá ngoại — hai database
 * tách rời, xem giải thích trong {@code database/03_booking_db.sql}.
 *
 * <p>Đơn {@code PENDING} có {@code expiresAt} = mốc giữ ghế (mặc định 10 phút).
 * Job quét {@code status = PENDING AND expiresAt < now} để trả ghế và đánh
 * {@code EXPIRED}.
 */
@Entity
@Table(name = "bookings")
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	/** Id người dùng bên auth_db (không FK). */
	@Column(name = "user_id", nullable = false)
	private Integer userId;

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

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (status == null) {
			status = BookingStatus.PENDING;
		}
		if (totalPrice == null) {
			totalPrice = BigDecimal.ZERO;
		}
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
}
