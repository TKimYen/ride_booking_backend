package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.util.LikePattern;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, Integer> {

	Optional<Location> findBySlug(String slug);

	/**
	 * Gợi ý cho dropdown "Nơi đi" / "Nơi đến".
	 *
	 * <p>Escape ký tự LIKE vì tên tỉnh do Admin nhập và từ khoá do người dùng gõ
	 * được đưa thẳng vào điều kiện. Bỏ escape thì gõ {@code %} sẽ trả về toàn bộ
	 * danh sách, còn {@code _} khớp bất kỳ ký tự nào — không phải lỗi bảo mật,
	 * nhưng gợi ý sai lệch thì người dùng tưởng hệ thống hỏng.
	 *
	 * <p>So khớp không phân biệt hoa thường, và chỉ tìm ở đầu chuỗi — hành vi quen
	 * thuộc kiểu "Hà" ra "Hà Nội", "Hà Giang".
	 */
	@Query("""
			SELECT l FROM Location l
			WHERE lower(l.name) LIKE lower(concat(:escaped, '%'))
			ORDER BY l.name
			""")
	List<Location> suggest(@Param("escaped") String escapedKeyword, Pageable pageable);
}