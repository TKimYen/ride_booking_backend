package com.ridebooking.booking.entity;

import com.ridebooking.booking.enums.SeatStatus;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng {@code trip_seats} — một ghế cụ thể trong một chuyến.
 *
 * <p>Bảng lớn nhất hệ thống: một chuyến có 29 ghế cũng đã 29 dòng, và chạy cả
 * catalog lẫn lịch sử đặt vé đều đọc bảng này.
 *
 * <p>Ràng buộc {@code UNIQUE (trip_id, seat_name)} do DB đảm bảo — không có nó thì
 * chạy lại logic sinh ghế sẽ tạo hai dòng cùng tên "A01" trong một chuyến và sơ
 * đồ ghế hiện trùng chỗ trống giả.
 *
 * <p>Quy tắc bất di bất dịch: mọi chuyển trạng thái đều phải khoá dòng này bằng
 * {@code SELECT ... FOR UPDATE} trước khi ghi. Nếu không, hai người có thể cùng
 * đọc thấy {@code AVAILABLE} rồi cùng ghi, và một ghế bị bán hai lần. Phần giữ
 * ghế thuộc Giai đoạn 2.
 */
@Entity
@Table(name = "trip_seats")
public class TripSeat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false)
	private Trip trip;

	/** Tên hiển thị trên sơ đồ ghế: A1, B2, FL... */
	@Column(name = "seat_name", nullable = false, length = 10)
	private String seatName;

	/** 1 = tầng dưới, 2 = tầng trên. */
	@Column(name = "floor", nullable = false)
	private Integer floor;

	/** STANDARD | DOUBLE | SINGLE. Xem {@code BusType.SeatType}. */
	@Column(name = "seat_type", nullable = false, length = 40)
	private String seatType;

	/** Cộng thêm vào {@code trips.base_price} cho riêng ghế này. */
	@Column(name = "extra_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal extraPrice = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private SeatStatus status = SeatStatus.AVAILABLE;

	/**
	 * Hạn giữ chỗ. Có nghĩa khi {@code status = HOLDING}, và PHẢI null ở mọi
	 * trạng thái khác — DB có CHECK chặn vi phạm, vì lệch dữ liệu ở đây làm job
	 * quét xử lý sai.
	 */
	@Column(name = "holding_until")
	private LocalDateTime holdingUntil;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	/** Giá vé thực tế của ghế này: giá chuyến cộng phần chênh lệch. */
	public BigDecimal effectivePrice(BigDecimal tripBasePrice) {
		return tripBasePrice.add(extraPrice);
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

	public String getSeatName() {
		return seatName;
	}

	public void setSeatName(String seatName) {
		this.seatName = seatName;
	}

	public Integer getFloor() {
		return floor;
	}

	public void setFloor(Integer floor) {
		this.floor = floor;
	}

	public String getSeatType() {
		return seatType;
	}

	public void setSeatType(String seatType) {
		this.seatType = seatType;
	}

	public BigDecimal getExtraPrice() {
		return extraPrice;
	}

	public void setExtraPrice(BigDecimal extraPrice) {
		this.extraPrice = extraPrice;
	}

	public SeatStatus getStatus() {
		return status;
	}

	public void setStatus(SeatStatus status) {
		this.status = status;
	}

	public LocalDateTime getHoldingUntil() {
		return holdingUntil;
	}

	public void setHoldingUntil(LocalDateTime holdingUntil) {
		this.holdingUntil = holdingUntil;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}