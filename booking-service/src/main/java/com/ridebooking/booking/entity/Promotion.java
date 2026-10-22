package com.ridebooking.booking.entity;

import com.ridebooking.booking.enums.DiscountType;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Bảng {@code promotions} — mã giảm giá cho tab Giảm giá và block Ưu đãi nổi bật.
 *
 * <p>{@code company} / {@code route} NULL nghĩa là áp dụng toàn hệ thống.
 * {@code usedCount} chỉ tăng khi đơn chuyển PAID, không tăng lúc giữ ghế, để đơn
 * hết hạn không ăn mất lượt dùng mã.
 */
@Entity
@Table(name = "promotions", uniqueConstraints = @UniqueConstraint(
		name = "uk_promotions_code", columnNames = "code"))
public class Promotion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "banner_url", length = 500)
	private String bannerUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "discount_type", nullable = false, length = 10)
	private DiscountType discountType;

	/** PERCENT: 1..100; FIXED: số tiền. */
	@Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountValue;

	/** Trần số tiền giảm cho kiểu PERCENT; NULL = không giới hạn. */
	@Column(name = "max_discount", precision = 12, scale = 2)
	private BigDecimal maxDiscount;

	@Column(name = "min_order_value", nullable = false, precision = 12, scale = 2)
	private BigDecimal minOrderValue = BigDecimal.ZERO;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id")
	private BusCompany company;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "route_id")
	private Route route;

	@Column(name = "starts_at", nullable = false)
	private LocalDateTime startsAt;

	@Column(name = "ends_at", nullable = false)
	private LocalDateTime endsAt;

	/** NULL = không giới hạn lượt dùng. */
	@Column(name = "usage_limit")
	private Integer usageLimit;

	@Column(name = "used_count", nullable = false)
	private Integer usedCount = 0;

	@Column(name = "is_featured", nullable = false)
	private boolean featured;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (minOrderValue == null) {
			minOrderValue = BigDecimal.ZERO;
		}
		if (usedCount == null) {
			usedCount = 0;
		}
	}

	/** Đang bật, trong thời hạn và còn lượt dùng tại thời điểm {@code now}. */
	public boolean isUsableAt(LocalDateTime now) {
		return active
				&& !now.isBefore(startsAt)
				&& now.isBefore(endsAt)
				&& (usageLimit == null || usedCount < usageLimit);
	}

	/**
	 * Số tiền giảm cho một đơn có tổng {@code subtotal}; 0 nếu chưa đạt giá trị
	 * tối thiểu. Không bao giờ vượt quá {@code subtotal}.
	 */
	public BigDecimal discountFor(BigDecimal subtotal) {
		if (subtotal.compareTo(minOrderValue) < 0) {
			return BigDecimal.ZERO;
		}
		BigDecimal discount = discountType == DiscountType.PERCENT
				? subtotal.multiply(discountValue).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
				: discountValue;
		if (maxDiscount != null && discount.compareTo(maxDiscount) > 0) {
			discount = maxDiscount;
		}
		return discount.min(subtotal);
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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getBannerUrl() {
		return bannerUrl;
	}

	public void setBannerUrl(String bannerUrl) {
		this.bannerUrl = bannerUrl;
	}

	public DiscountType getDiscountType() {
		return discountType;
	}

	public void setDiscountType(DiscountType discountType) {
		this.discountType = discountType;
	}

	public BigDecimal getDiscountValue() {
		return discountValue;
	}

	public void setDiscountValue(BigDecimal discountValue) {
		this.discountValue = discountValue;
	}

	public BigDecimal getMaxDiscount() {
		return maxDiscount;
	}

	public void setMaxDiscount(BigDecimal maxDiscount) {
		this.maxDiscount = maxDiscount;
	}

	public BigDecimal getMinOrderValue() {
		return minOrderValue;
	}

	public void setMinOrderValue(BigDecimal minOrderValue) {
		this.minOrderValue = minOrderValue;
	}

	public BusCompany getCompany() {
		return company;
	}

	public void setCompany(BusCompany company) {
		this.company = company;
	}

	public Route getRoute() {
		return route;
	}

	public void setRoute(Route route) {
		this.route = route;
	}

	public LocalDateTime getStartsAt() {
		return startsAt;
	}

	public void setStartsAt(LocalDateTime startsAt) {
		this.startsAt = startsAt;
	}

	public LocalDateTime getEndsAt() {
		return endsAt;
	}

	public void setEndsAt(LocalDateTime endsAt) {
		this.endsAt = endsAt;
	}

	public Integer getUsageLimit() {
		return usageLimit;
	}

	public void setUsageLimit(Integer usageLimit) {
		this.usageLimit = usageLimit;
	}

	public Integer getUsedCount() {
		return usedCount;
	}

	public void setUsedCount(Integer usedCount) {
		this.usedCount = usedCount;
	}

	public boolean isFeatured() {
		return featured;
	}

	public void setFeatured(boolean featured) {
		this.featured = featured;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
