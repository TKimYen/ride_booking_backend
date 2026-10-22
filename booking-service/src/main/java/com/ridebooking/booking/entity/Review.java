package com.ridebooking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Bảng {@code reviews} — đánh giá chuyến sau khi đi.
 *
 * <p>{@code userId} là {@code INTEGER} thuần, KHÔNG phải khoá ngoại: nằm ở
 * {@code user_db} nên không tham chiếu được, và một khoá ngoái chéo database sẽ
 * làm booking-service không xoá được database mà không phá dữ liệu nơi khác.
 * Tên người đánh giá lấy qua user-service.
 *
 * <p>{@code booking} chứng minh người viết đã đi chuyến; {@code company} chép
 * từ {@code trip.company} để tính điểm theo nhà xe. Cả hai NULL được vì đánh
 * giá seed cũ không gắn đơn. Đánh giá {@code hidden} không hiện và không tính
 * vào điểm trung bình.
 */
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(
		name = "uk_reviews_booking", columnNames = "booking_id"))
public class Review {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "user_id", nullable = false)
	private Integer userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false)
	private Trip trip;

	/** Đơn đã đi của người viết; UNIQUE nên mỗi đơn một đánh giá. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id")
	private BusCompany company;

	/** Admin ẩn bình luận vi phạm. Boolean vì lý do giống {@code Route.featured}. */
	@Column(name = "is_hidden")
	private Boolean hidden = false;

	/** 1..5, có CHECK ở DB. */
	@Column(name = "rating", nullable = false)
	private Integer rating;

	@Column(name = "comment", columnDefinition = "TEXT")
	private String comment;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (hidden == null) {
			hidden = false;
		}
		if (company == null && trip != null) {
			company = trip.getCompany();
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
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

	public Integer getRating() {
		return rating;
	}

	public void setRating(Integer rating) {
		this.rating = rating;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public Booking getBooking() {
		return booking;
	}

	public void setBooking(Booking booking) {
		this.booking = booking;
	}

	public BusCompany getCompany() {
		return company;
	}

	public void setCompany(BusCompany company) {
		this.company = company;
	}

	public boolean isHidden() {
		return Boolean.TRUE.equals(hidden);
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
}
