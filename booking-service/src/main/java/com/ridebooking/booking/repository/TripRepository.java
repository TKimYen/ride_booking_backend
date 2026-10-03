package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.TripStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Truy vấn chuyến xe.
 *
 * <p>Dùng {@link Specification} (JPA Criteria) chứ không nối chuỗi JPQL: bộ lọc
 * của Trang Danh sách kết quả có tám điều kiện tuỳ chọn, ghép tay sẽ ra một
 * khối {@code if} khó đọc và dễ sót nhánh. Mọi giá trị đều đi qua tham số hoá của
 * Hibernate, nên ký tự đặc biệt trong từ khoá không sinh SQL.
 */
public interface TripRepository extends JpaRepository<Trip, Integer>, JpaSpecificationExecutor<Trip> {

    /**
     * Bộ lọc của Trang Danh sách kết quả.
     *
     * <p>Mỗi điều kiện {@code null} thì bỏ qua. Ngày đi KHÔNG truyền vào đây:
     * service đã quy đổi nó thành khoảng {@code [00:00, 23:59:59.999999999]}
     * của ngày đó trước, để điều kiện thời gian chỉ có một nơi chịu trách nhiệm.
     * Sắp xếp KHÔNG nằm ở đây mà để {@code Pageable}, vì cột sắp xếp lấy từ
     * allowlist {@code TripSort}.
     */
    static Specification<Trip> matches(
            Integer originId,
            Integer destinationId,
            Integer companyId,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            java.math.BigDecimal minPrice,
            java.math.BigDecimal maxPrice,
            TripStatus status) {

        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (originId != null || destinationId != null) {
                // INNER JOIN vì chỉ lọc khi có điều kiện địa điểm, nên không sinh
                // bản ghi nhân bản chỉ vì điều kiện không dùng tới join.
                Join<Object, Object> route = root.join("route", JoinType.INNER);
                if (originId != null) {
                    predicates.add(builder.equal(route.get("origin").get("id"), originId));
                }
                if (destinationId != null) {
                    predicates.add(builder.equal(route.get("destination").get("id"), destinationId));
                }
            }
            if (companyId != null) {
                predicates.add(builder.equal(root.get("company").get("id"), companyId));
            }
            if (fromTime != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("departureTime"), fromTime));
            }
            if (toTime != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("departureTime"), toTime));
            }
            if (minPrice != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("basePrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("basePrice"), maxPrice));
            }

            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}