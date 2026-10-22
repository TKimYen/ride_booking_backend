package com.ridebooking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Bảng {@code booking_details} — một ghế trong một đơn.
 *
 * <p>{@code price} là giá chốt lúc mua, không suy lại từ {@code trips.base_price}
 * hay {@code trip_seats.extra_price}, để hoá đơn cũ không đổi khi nhà xe tăng giá.
 *
 * <p>Không có UNIQUE(seat_id) một cách cố ý: ghế được bán lại sau khi đơn cũ bị
 * huỷ/hết hạn. Tính "một ghế chỉ thuộc một đơn đang hiệu lực" được bảo vệ bằng
 * khoá dòng {@code trip_seats.status}, không bằng ràng buộc bảng này.
 */
@Entity
@Table(name = "booking_details")
public class BookingDetail {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seat_id", nullable = false)
	private TripSeat seat;

	@Column(name = "price", nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	/** Tên hành khách ngồi ghế này; NULL thì dùng {@code contactName} của đơn. */
	@Column(name = "passenger_name", length = 255)
	private String passengerName;

	@Column(name = "passenger_phone", length = 20)
	private String passengerPhone;

	public BookingDetail() {
	}

	public BookingDetail(Booking booking, TripSeat seat, BigDecimal price) {
		this.booking = booking;
		this.seat = seat;
		this.price = price;
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

	public TripSeat getSeat() {
		return seat;
	}

	public void setSeat(TripSeat seat) {
		this.seat = seat;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getPassengerName() {
		return passengerName;
	}

	public void setPassengerName(String passengerName) {
		this.passengerName = passengerName;
	}

	public String getPassengerPhone() {
		return passengerPhone;
	}

	public void setPassengerPhone(String passengerPhone) {
		this.passengerPhone = passengerPhone;
	}
}
