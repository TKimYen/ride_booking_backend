package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.CompanyPolicy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyPolicyRepository extends JpaRepository<CompanyPolicy, Integer> {

	List<CompanyPolicy> findByCompanyIdOrderBySortOrderAscIdAsc(Integer companyId);
}
