package com.ridebooking.booking.entity;

import com.ridebooking.booking.enums.PolicyType;
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

/**
 * Bảng {@code company_policies} — văn bản chính sách hiển thị ở tab Chính sách.
 *
 * <p>Chỉ để đọc. Quy tắc hoàn tiền mà hệ thống tự tính nằm ở
 * {@link CancellationRule}, không parse từ nội dung ở đây.
 */
@Entity
@Table(name = "company_policies")
public class CompanyPolicy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", nullable = false)
	private BusCompany company;

	@Enumerated(EnumType.STRING)
	@Column(name = "policy_type", nullable = false, length = 30)
	private PolicyType policyType;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "content", nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 1;

	@PrePersist
	void onCreate() {
		if (sortOrder == null) {
			sortOrder = 1;
		}
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

	public PolicyType getPolicyType() {
		return policyType;
	}

	public void setPolicyType(PolicyType policyType) {
		this.policyType = policyType;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}
}
