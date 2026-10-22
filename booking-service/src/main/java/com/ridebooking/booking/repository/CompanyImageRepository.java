package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.CompanyImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyImageRepository extends JpaRepository<CompanyImage, Integer> {

	List<CompanyImage> findByCompanyIdOrderBySortOrderAscIdAsc(Integer companyId);
}
