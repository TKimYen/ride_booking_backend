package com.ridebooking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Bảng {@code amenities} — danh mục tiện ích dùng chung (Wifi, Nước uống...).
 * Nhà xe nào có tiện ích nào nằm ở bảng nối {@code company_amenities}, xem
 * {@link BusCompany#getAmenities()}.
 */
@Entity
@Table(name = "amenities", uniqueConstraints = @UniqueConstraint(
		name = "uk_amenities_code", columnNames = "code"))
public class Amenity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	/** Mã ổn định, ví dụ {@code WIFI}; dùng khi seed và khi frontend chọn icon mặc định. */
	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	/** Tên icon phía frontend, không phải URL ảnh. */
	@Column(name = "icon", length = 60)
	private String icon;

	public Amenity() {
	}

	public Amenity(String code, String name, String icon) {
		this.code = code;
		this.name = name;
		this.icon = icon;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}
}
