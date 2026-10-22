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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng {@code routes} — tuyến đường, ví dụ "Hà Nội - Sapa".
 *
 * <p>Cả {@code originId} và {@code destinationId} trỏ vào cùng bảng
 * {@code locations}, tức là quan hệ trong CÙNG database nên dùng khoá ngoại thật
 * được (khác với {@code bookings.user_id} là id sinh ở service khác).
 *
 * <p>Hai quan hệ dùng {@code LAZY} vì danh sách tuyến luôn đi kèm tên địa điểm;
 * load EAGER sẽ tạo N+1 query khi lấy nhiều tuyến. Tên địa điểm được nạp tường
 * minh trong service bằng {@code join fetch} khi cần.
 */
@Entity
@Table(name = "routes")
public class Route {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "origin_id", nullable = false)
	private Location origin;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "destination_id", nullable = false)
	private Location destination;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "distance_km", precision = 8, scale = 2)
	private BigDecimal distanceKm;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	/** Ảnh cho block "Tuyến đường phổ biến" ở Trang chủ. */
	@Column(name = "image_url", length = 500)
	private String imageUrl;

	/**
	 * Admin chọn hiện ở Trang chủ. DB đặt NOT NULL DEFAULT FALSE; entity để
	 * {@code Boolean} vì {@code ddl-auto=update} không thêm được cột NOT NULL vào
	 * bảng đã có dữ liệu. Đọc qua {@link #isFeatured()} để NULL coi như false.
	 */
	@Column(name = "is_featured")
	private Boolean featured = false;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (featured == null) {
			featured = false;
		}
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Location getOrigin() {
		return origin;
	}

	public void setOrigin(Location origin) {
		this.origin = origin;
	}

	public Location getDestination() {
		return destination;
	}

	public void setDestination(Location destination) {
		this.destination = destination;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getDistanceKm() {
		return distanceKm;
	}

	public void setDistanceKm(BigDecimal distanceKm) {
		this.distanceKm = distanceKm;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public boolean isFeatured() {
		return Boolean.TRUE.equals(featured);
	}

	public void setFeatured(boolean featured) {
		this.featured = featured;
	}
}
