package com.ridebooking.booking.entity;

import com.ridebooking.booking.enums.PointType;
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
import java.time.LocalDateTime;

/**
 * Bảng {@code stop_points} — điểm đón và điểm trả của một chuyến.
 *
 * <p>Trên Trang chi tiết chuyến (Thành viên 1) khách cần biết xe đón ở đâu, dọc
 * theo thứ tự nào, và dự kiến mấy giờ. Khối thông tin này là điểm dừng, khác với
 * địa điểm đầu/cuối nằm ở {@code routes}.
 *
 * <p>{@code stopOrder} được service cấp tự động, không nhận từ request: xếp tay
 * thì hai điểm cùng số thứ tự và chuỗi "đón lần 1, lần 1" trên UI.
 */
@Entity
@Table(name = "stop_points")
public class StopPoint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false)
	private Trip trip;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "address", length = 300)
	private String address;

	@Enumerated(EnumType.STRING)
	@Column(name = "point_type", nullable = false, length = 20)
	private PointType pointType;

	@Column(name = "estimated_time")
	private LocalDateTime estimatedTime;

	@Column(name = "stop_order", nullable = false)
	private Integer stopOrder = 1;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Trip getTrip() {
		return trip;
	}

	public void setTrip(Trip trip) {
		this.trip = trip;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public PointType getPointType() {
		return pointType;
	}

	public void setPointType(PointType pointType) {
		this.pointType = pointType;
	}

	public LocalDateTime getEstimatedTime() {
		return estimatedTime;
	}

	public void setEstimatedTime(LocalDateTime estimatedTime) {
		this.estimatedTime = estimatedTime;
	}

	public Integer getStopOrder() {
		return stopOrder;
	}

	public void setStopOrder(Integer stopOrder) {
		this.stopOrder = stopOrder;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
