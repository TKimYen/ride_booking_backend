package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.BusCompany;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusCompanyRepository extends JpaRepository<BusCompany, Integer> {

	Optional<BusCompany> findByNameIgnoreCase(String name);

	/** Tìm nhà xe theo tên, cho bộ lọc "theo nhà xe" ở Trang Danh sách kết quả. */
	@Query("""
			SELECT c FROM BusCompany c
			WHERE lower(c.name) LIKE lower(concat(:escaped, '%'))
			ORDER BY c.name
			""")
	List<BusCompany> suggest(@Param("escaped") String escapedKeyword, Pageable pageable);
}