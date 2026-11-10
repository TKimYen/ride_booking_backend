package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.StopPoint;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.PointType;
import com.ridebooking.booking.enums.TripStatus;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn chuyến xe.
 *
 * <p>Dùng {@link Specification} (JPA Criteria) chứ không nối chuỗi JPQL: bộ lọc
 * của Trang Danh sách kết quả có tám điều kiện tuỳ chọn, ghép tay sẽ ra một
 * khối {@code if} khó đọc và dễ sót nhánh. Mọi giá trị đều đi qua tham số hoá của
 * Hibernate, nên ký tự đặc biệt trong từ khoá không sinh SQL.
 */
public interface TripRepository extends JpaRepository<Trip, Integer>, JpaSpecificationExecutor<Trip> {

    /** Nạp sẵn tuyến và nhà xe để dựng một trang chuyến mà không sinh N+1. */
    @Override
    @EntityGraph(attributePaths = {"route", "route.origin", "route.destination", "company"})
    Page<Trip> findAll(Specification<Trip> spec, Pageable pageable);

    /** Tuyến đã có chuyến (mọi trạng thái) khởi hành trong khoảng này chưa. Dùng cho bước bù chuyến mẫu. */
    boolean existsByRouteIdAndDepartureTimeBetween(Integer routeId, java.time.LocalDateTime from,
                                                   java.time.LocalDateTime to);

    /**
     * Đọc trạng thái mới nhất từ DB, không qua cache của phiên làm việc. Luồng giữ
     * ghế gọi lại sau khi có khoá ghế, để không bán vé của chuyến vừa bị huỷ.
     */
    @Query("SELECT t.status FROM Trip t WHERE t.id = :id")
    Optional<TripStatus> findStatusById(@Param("id") Integer id);

    /** Chuyển trạng thái có điều kiện; trả về 0 khi trạng thái hiện tại không còn là {@code from}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Trip t
            SET t.status = :to, t.updatedAt = :now
            WHERE t.id = :id AND t.status = :from
            """)
    int transitionStatus(@Param("id") Integer id,
                         @Param("from") TripStatus from,
                         @Param("to") TripStatus to,
                         @Param("now") LocalDateTime now);

    /** Mọi chuyến đang chạy đã qua giờ đến thành COMPLETED, trong một câu UPDATE. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Trip t
            SET t.status = :completed, t.updatedAt = :now
            WHERE t.status = :active AND t.arrivalTime < :now
            """)
    int completeArrivedTrips(@Param("completed") TripStatus completed,
                             @Param("active") TripStatus active,
                             @Param("now") LocalDateTime now);

    /** Giá cơ bản thấp nhất của các chuyến sắp chạy theo tuyến; mỗi dòng là [routeId, giá]. */
    @Query("""
            SELECT t.route.id, MIN(t.basePrice)
            FROM Trip t
            WHERE t.route.id IN :routeIds AND t.status = :status AND t.departureTime > :now
            GROUP BY t.route.id
            """)
    List<Object[]> findMinPriceByRouteIds(@Param("routeIds") Collection<Integer> routeIds,
                                          @Param("status") TripStatus status,
                                          @Param("now") LocalDateTime now);

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
            BigDecimal minPrice,
            BigDecimal maxPrice,
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

    /**
     * Bộ lọc mở rộng [4.4] của Trang Danh sách kết quả, ghép thêm vào {@link #matches}.
     *
     * <p>Danh sách rỗng hoặc null thì bỏ qua điều kiện đó. Điểm đón/trả so theo
     * TÊN chứ không theo id: điểm dừng gắn với từng chuyến, nên "Bến xe Mỹ Đình"
     * của hai chuyến là hai dòng khác id. Dùng {@code EXISTS} chứ không JOIN để một
     * chuyến có nhiều điểm khớp không bị lặp trong trang kết quả.
     */
    static Specification<Trip> matchesExtra(Collection<Integer> companyIds,
                                           Collection<BusType> busTypes,
                                           Collection<String> pickupNames,
                                           Collection<String> dropoffNames) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (companyIds != null && !companyIds.isEmpty()) {
                predicates.add(root.get("company").get("id").in(companyIds));
            }
            if (busTypes != null && !busTypes.isEmpty()) {
                predicates.add(root.get("busType").in(busTypes));
            }
            for (var filter : List.of(
                    java.util.Map.entry(PointType.PICKUP, pickupNames == null ? List.<String>of() : pickupNames),
                    java.util.Map.entry(PointType.DROPOFF, dropoffNames == null ? List.<String>of() : dropoffNames))) {
                if (filter.getValue().isEmpty()) {
                    continue;
                }
                Subquery<Integer> stop = query.subquery(Integer.class);
                var sp = stop.from(StopPoint.class);
                stop.select(sp.get("id")).where(
                        builder.equal(sp.get("trip"), root),
                        builder.equal(sp.get("pointType"), filter.getKey()),
                        sp.get("name").in(filter.getValue()));
                predicates.add(builder.exists(stop));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** Số chuyến theo trạng thái khởi hành sau mốc này; Dashboard [3.5] đếm chuyến sắp chạy. */
    @Query("""
            SELECT count(t) FROM Trip t
            WHERE t.status = :status AND t.departureTime > :after
              AND (:companyId IS NULL OR t.company.id = :companyId)
            """)
    long countUpcoming(@Param("status") TripStatus status, @Param("after") LocalDateTime after,
                       @Param("companyId") Integer companyId);
}
