package com.ridebooking.booking.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Danh sách origin được phép gọi API.
 *
 * <p>Không dùng {@code *} + {@code allowCredentials} vì Spring chặn khi kết hợp
 * hai thứ đó; nếu muốn mở rộng thì khai báo origin cụ thể qua biến
 * {@code CORS_ALLOWED_ORIGINS} (phân cách bằng dấu phẩy).
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** Origin mặc định cho môi trường dev: Vite và CRA. */
    private List<String> allowedOrigins = List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://127.0.0.1:5173");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}