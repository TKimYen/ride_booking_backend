package com.ridebooking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Bảng {@code bus_companies} — nhà xe, dùng cho bộ lọc "theo nhà xe". */
@Entity
@Table(name = "bus_companies")
public class BusCompany {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "name", nullable = false, length = 150, unique = true)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "logo_url", length = 500)
	private String logoUrl;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/**
	 * Tiện ích của nhà xe (tab Tiện ích). LAZY: danh sách nhà xe ở bộ lọc không
	 * cần tiện ích, chỉ Trang chi tiết mới nạp.
	 */
	@ManyToMany
	@JoinTable(name = "company_amenities",
			joinColumns = @JoinColumn(name = "company_id"),
			inverseJoinColumns = @JoinColumn(name = "amenity_id"))
	private Set<Amenity> amenities = new LinkedHashSet<>();

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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLogoUrl() {
		return logoUrl;
	}

	public void setLogoUrl(String logoUrl) {
		this.logoUrl = logoUrl;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public Set<Amenity> getAmenities() {
		return amenities;
	}

	public void setAmenities(Set<Amenity> amenities) {
		this.amenities = amenities;
	}
}
