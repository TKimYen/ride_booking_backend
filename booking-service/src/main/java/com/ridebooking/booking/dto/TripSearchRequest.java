package com.ridebooking.booking.dto;

import com.ridebooking.booking.enums.TripSort;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Bộ lọc cho Trang Danh sách kết quả và API tìm kiếm chuyến.
 *
 * <p>Mọi trường đều tuỳ chọn. Bỏ trống tất cả thì trả chuyến ACTIVE sắp theo giờ
 * đi — đó là mặc định hợp lý và cũng là truy vấn được index
 * {@code ix_trips_status_departure} phục vụ.
 *
 * <p>Không có trường {@code keyword} tự do: tìm kiếm ở đây là lọc theo
 * cặp địa điểm cụ thể, không phải tìm toàn văn. Tìm toàn văn chuyến xe là điểm
 * cộng bằng AI Gemini, làm sau.
 */
@Schema(description = "Bộ lọc tìm chuyến xe")
public record TripSearchRequest(

        @Schema(description = "id địa điểm xuất phát", example = "1")
        Integer originId,

        @Schema(description = "id địa điểm đến", example = "8")
        Integer destinationId,

        @Schema(description = "id nhà xe, lấy từ danh sách /api/bus-companies")
        Integer companyId,

        @Schema(description = "Ngày đi. Bỏ trống thì không lọc theo ngày", example = "2026-10-05")
        LocalDate travelDate,

        @Schema(description = "Giờ khởi hành sớm nhất", example = "06:00:00")
        LocalTime earliestDeparture,

        @Schema(description = "Giờ khởi hành muộn nhất", example = "18:00:00")
        LocalTime latestDeparture,

        @Schema(description = "Giá vé thấp nhất", example = "150000")
        BigDecimal minPrice,

        @Schema(description = "Giá vé cao nhất", example = "500000")
        BigDecimal maxPrice,

        @Schema(description = """
                Cách sắp xếp. Giá trị lạ thì dùng mặc định departureAsc.
                Chấp nhận cả camelCase (priceAsc), snake_case (price_asc) và
                aliases departureTimeAsc/departureTimeDesc.""",
                allowableValues = {"departureAsc", "departureDesc", "priceAsc", "priceDesc", "newest"},
                example = "priceAsc")
        String sort) {

    /** Kiểm tra giá trị đã chuẩn hoá qua allowlist. */
    public TripSort sortOrDefault() {
        return TripSort.from(sort);
    }
}