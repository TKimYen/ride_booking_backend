package com.ridebooking.booking.entity;

import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.TripStatus;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng {@code trips} — một chuyến xe cụ thể (tuyến + nhà xe + giờ + loại xe + giá).
 *
 * <p>Phân biệt với {@code routes}: tuyến là "Hà Nội - Sa Pa" và tồn tại lâu dài,
 * còn chuyến là một xe chạy lúc 06:00 ngày mai. Trang Danh sách kết quả lọc theo
 * cả hai.
 */
@Entity
@Table(name = "trips")
public class Trip {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "route_id", nullable = false)
	private Route route;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", nullable = false)
	private BusCompany company;

	/** Quyết định sơ đồ ghế, xem {@link BusType}. */
	@Enumerated(EnumType.STRING)
	@Column(name = "bus_type", nullable = false, length = 40)
	private BusType busType;

	@Column(name = "departure_time", nullable = false)
	private LocalDateTime departureTime;

	@Column(name = "arrival_time", nullable = false)
	private LocalDateTime arrivalTime;

	/** Giá vé cơ bản. Ghế có thể cộng thêm {@code extraPrice}. */
	@Column(name = "base_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal basePrice;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private TripStatus status = TripStatus.ACTIVE;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (status == null) {
			status = TripStatus.ACTIVE;
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

	public Route getRoute() {
		return route;
	}

	public void setRoute(Route route) {
		this.route = route;
	}

	public BusCompany getCompany() {
		return company;
	}

	public void setCompany(BusCompany company) {
		this.company = company;
	}

	public BusType getBusType() {
		return busType;
	}

	public void setBusType(BusType busType) {
		this.busType = busType;
	}

	public LocalDateTime getDepartureTime() {
		return departureTime;
	}

	public void setDepartureTime(LocalDateTime departureTime) {
		this.departureTime = departureTime;
	}

	public LocalDateTime getArrivalTime() {
		return arrivalTime;
	}

	public void setArrivalTime(LocalDateTime arrivalTime) {
		this.arrivalTime = arrivalTime;
	}

	public BigDecimal getBasePrice() {
		return basePrice;
	}

	public void setBasePrice(BigDecimal basePrice) {
		this.basePrice = basePrice;
	}

	public TripStatus getStatus() {
		return status;
	}

	public void setStatus(TripStatus status) {
		this.status = status;
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

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}