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
import jakarta.persistence.UniqueConstraint;

/**
 * Bảng {@code cancellation_rules} — mốc hoàn tiền khi huỷ vé đã thanh toán.
 *
 * <p>Huỷ trước giờ khởi hành ít nhất {@code hoursBefore} giờ thì hoàn
 * {@code refundPercent}%. Áp mốc có {@code hoursBefore} lớn nhất mà khách còn
 * thoả; không thoả mốc nào thì không hoàn.
 */
@Entity
@Table(name = "cancellation_rules", uniqueConstraints = @UniqueConstraint(
		name = "uk_cancellation_rules_company_hours", columnNames = {"company_id", "hours_before"}))
public class CancellationRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", nullable = false)
	private BusCompany company;

	@Column(name = "hours_before", nullable = false)
	private Integer hoursBefore;

	/** 0..100, có CHECK ở DB. */
	@Column(name = "refund_percent", nullable = false)
	private Integer refundPercent;

	public CancellationRule() {
	}

	public CancellationRule(BusCompany company, Integer hoursBefore, Integer refundPercent) {
		this.company = company;
		this.hoursBefore = hoursBefore;
		this.refundPercent = refundPercent;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public BusCompany getCompany() {
		return company;
	}

	public void setCompany(BusCompany company) {
		this.company = company;
	}

	public Integer getHoursBefore() {
		return hoursBefore;
	}

	public void setHoursBefore(Integer hoursBefore) {
		this.hoursBefore = hoursBefore;
	}

	public Integer getRefundPercent() {
		return refundPercent;
	}

	public void setRefundPercent(Integer refundPercent) {
		this.refundPercent = refundPercent;
	}
}
