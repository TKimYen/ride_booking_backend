package com.ridebooking.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình OpenAPI cho booking-service.
 *
 * <p>UI: http://localhost:8083/swagger-ui/index.html
 * JSON: http://localhost:8083/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingServiceOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Ride Booking - Booking Service API")
                        .version("v1.0.0")
                        .description("""
                                Module Đặt vé: Catalog chuyến xe, sơ đồ ghế, tuyến điểm, đánh giá.
                                Phục vụ nhóm Frontend (Thành viên 1 và 3) và trang Quản lý của Admin.

                                **Luồng chính:**
                                1. Khách tìm chuyến: `GET /api/trips`
                                2. Xem sơ đồ ghế: `GET /api/seats/trip/{tripId}`
                                3. Admin thêm chuyến: `POST /api/admin/trips` — ghế được sinh tự động

                                **Quyền:** các API đọc catalog không cần đăng nhập.
                                `/api/admin/**` cần token của tài khoản `ADMIN`.

                                **Database:** `booking_db` — bảng `locations`, `bus_companies`, `routes`,
                                `trips`, `stop_points`, `trip_seats`, `reviews`.
                                Giai đoạn 2 mới mở khoá `bookings`, `booking_details`, `payments`.

                                **Ghế:** không nhập tay. Mỗi chuyến sinh ghế từ loại xe, và
                                `seat_name` là duy nhất trong cả chuyến nên xe hai tầng ghi kèm số tầng
                                vào tên (`1A1`, `2A1`).
                                """)
                        .contact(new Contact().name("Thành viên 3 - Module Đặt vé & Thanh toán"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Gọi trực tiếp booking-service"),
                        new Server().url("http://localhost:8080").description("Qua api-gateway (khuyến nghị)")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập token lấy từ auth-service"))
                        .addSchemas("ErrorResponse", new Schema<>()
                                .type("object")
                                .description("Format lỗi thống nhất của toàn hệ thống")
                                .properties(Map.of(
                                        "timestamp", new Schema<>().type("string").example("2026-09-30T16:04:52.62Z"),
                                        "status", new Schema<>().type("integer").example(403),
                                        "error", new Schema<>().type("string").example("Forbidden"),
                                        "message", new Schema<>().type("string")
                                                .example("Bạn không có quyền xem hồ sơ này"),
                                        "path", new Schema<>().type("string").example("/api/trips/17")))));
    }
}