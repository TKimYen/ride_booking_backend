package com.ridebooking.payment.config;

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
 * Cấu hình OpenAPI cho payment-service.
 *
 * <p>UI: http://localhost:8084/swagger-ui/index.html
 * JSON: http://localhost:8084/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Ride Booking - Payment Service API (Stripe)")
                        .version("v1.0.0")
                        .description("""
                                Module Thanh toán: tạo Stripe Checkout Session và nhận webhook Stripe
                                để chốt đơn.

                                **Luồng chính:**
                                1. Khách giữ ghế ở booking-service (đơn PENDING, có hạn giữ ghế).
                                2. `POST /api/payments/checkout-session` — trả `checkoutUrl` của Stripe.
                                3. Khách trả tiền trên trang Stripe.
                                4. Stripe gọi `POST /api/payments/webhook` — payment-service xác nhận
                                   với booking-service và đơn chuyển PAID, ghế thành BOOKED.

                                **Không có database riêng:** bảng `payments` nằm trong `booking_db`
                                và chỉ được ghi qua API nội bộ của booking-service.

                                **Bảo mật:** `checkout-session` cần JWT. `webhook` công khai nhưng
                                bắt buộc có chữ ký `Stripe-Signature` hợp lệ.
                                """)
                        .contact(new Contact().name("Nhóm XeGo"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8084").description("Gọi trực tiếp payment-service"),
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
                                        "timestamp", new Schema<>().type("string").example("2026-10-02T10:00:00Z"),
                                        "status", new Schema<>().type("integer").example(409),
                                        "error", new Schema<>().type("string").example("Conflict"),
                                        "message", new Schema<>().type("string")
                                                .example("Đơn không còn ở trạng thái chờ thanh toán"),
                                        "path", new Schema<>().type("string")
                                                .example("/api/payments/checkout-session")))));
    }
}
