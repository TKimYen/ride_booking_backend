package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Route;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRepository extends JpaRepository<Route, Integer> {

	Optional<Route> findByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCase(String name);

	/**
	 * Các tuyến đi từ một địa điểm — dùng cho dropdown "Tuyến đường phổ biến"
	 * ở Trang chủ.
	 *
	 * <p>{@code join fetch} hai cột địa điểm vì service cần tên cả hai bên để
	 * hiển thị "Hà Nội - Sapa". Không có fetch thì mỗi dòng là một query nữa
	 * (N+1) mà bảng routes còn lâu mới lớn.
	 */
	@Query("""
			SELECT DISTINCT r FROM Route r
			JOIN FETCH r.origin
			JOIN FETCH r.destination
			WHERE r.origin.id = :originId
			ORDER BY r.name
			""")
	List<Route> findByOriginIdWithLocations(@Param("originId") Integer originId, Pageable pageable);

	/**
	 * Các tuyến kết nối tới một địa điểm, theo cả hai chiều: đi ra hoặc đi vào.
	 * Trang chủ cần cả hai vì khách có thể tìm chuyến đi từ một tỉnh lẻ.
	 */
	@Query("""
			SELECT DISTINCT r FROM Route r
			JOIN FETCH r.origin
			JOIN FETCH r.destination
			WHERE r.origin.id = :locationId OR r.destination.id = :locationId
			ORDER BY r.name
			""")
	List<Route> findTouchingLocation(@Param("locationId") Integer locationId, Pageable pageable);
}