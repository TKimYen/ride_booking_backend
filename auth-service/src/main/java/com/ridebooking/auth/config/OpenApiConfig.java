package com.ridebooking.auth.config;

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
 * Cấu hình OpenAPI cho auth-service.
 *
 * <p>UI: http://localhost:8081/swagger-ui/index.html
 * JSON: http://localhost:8081/v3/api-docs
 *
 * <p>Để test qua api-gateway thì bấm "Try it out" và đổi server sang
 * http://localhost:8088 (xem phần {@code servers}).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Ride Booking - Auth Service API")
                        .version("v1.0.0")
                        .description("""
                                Module Tài khoản & Phân quyền.

                                **Luồng chính:**
                                1. `POST /api/auth/register` hoặc `POST /api/auth/login` để lấy `accessToken`
                                2. Bấm "Authorize" ở góc phải, dán token vào
                                3. Các endpoint còn lại sẽ tự gắn header `Authorization: Bearer <token>`

                                **Database:** `auth_db` — bảng `users` (email, password BCrypt, role).
                                Phần hồ sơ cá nhân nằm ở `user_db.user_profiles`, do user-service sở hữu.
                                """)
                        .contact(new Contact().name("Nhóm XeGo"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Gọi trực tiếp auth-service"),
                        new Server().url("http://localhost:8088").description("Qua api-gateway (khuyến nghị)")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập token trả về từ API đăng nhập / đăng ký"))
                        .addSchemas("ErrorResponse", new Schema<>()
                                .type("object")
                                .description("Format lỗi thống nhất của toàn hệ thống")
                                .properties(Map.of(
                                        "timestamp", new Schema<>().type("string").example("2026-09-30T16:04:52.62Z"),
                                        "status", new Schema<>().type("integer").example(401),
                                        "error", new Schema<>().type("string").example("Unauthorized"),
                                        "message", new Schema<>().type("string")
                                                .example("Bạn chưa đăng nhập hoặc token không hợp lệ"),
                                        "path", new Schema<>().type("string").example("/api/auth/me")))));
    }
}