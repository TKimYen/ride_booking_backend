package com.ridebooking.user.config;

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
 * Cấu hình OpenAPI cho user-service.
 *
 * <p>UI: http://localhost:8082/swagger-ui/index.html
 * JSON: http://localhost:8082/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Ride Booking - User Service API")
                        .version("v1.0.0")
                        .description("""
                                Module Quản lý Cá nhân (nhóm Frontend: Trang Thông tin tài khoản).

                                **Luồng chính:**
                                1. Lấy token từ auth-service (`POST http://localhost:8081/api/auth/login`)
                                2. Bấm "Authorize" và dán token
                                3. Dùng `GET`/`PUT /api/users/me` để xem và sửa hồ sơ

                                **Database:** `user_db` — bảng `user_profiles`.
                                Mật khẩu và email đăng nhập nằm ở `auth_db` (auth-service), service này không đọc được.
                                `role` trong response là bản sao được đồng bộ từ auth-service.
                                """)
                        .contact(new Contact().name("Nhóm XeGo"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Gọi trực tiếp user-service"),
                        new Server().url("http://localhost:8088").description("Qua api-gateway (khuyến nghị)")))
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
                                        "path", new Schema<>().type("string").example("/api/users/5")))));
    }
}