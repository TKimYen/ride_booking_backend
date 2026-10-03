package com.ridebooking.gateway.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Cho phép frontend (Vite 5173 / CRA 3000) gọi API qua gateway.
 *
 * <p>KHÔNG dùng {@code allowedOriginPatterns("*")} cùng {@code allowCredentials(true)}:
 * khi đó gateway phản chiếu nguyên origin của request, nên bất kỳ trang nào cũng
 * gọi được API kèm cookie/header của người dùng. Danh sách origin lấy từ
 * {@code CORS_ALLOWED_ORIGINS}, cùng biến đó mà auth-service và user-service dùng,
 * nên chỉ cần khai báo một chỗ cho cả hệ thống.
 */
@Configuration
public class CorsConfig {

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Chỉ origin có trong danh sách mới được phép, và KHÔNG phản chiếu
        // wildcard.
        configuration.setAllowedOrigins(splitOrigins());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Dùng header Authorization (Bearer token) chứ không phải cookie phiên,
        // nên không cần allowCredentials. Bật credentials với danh sách origin
        // hữu hạn vẫn an toàn, nhưng tắt đi cho chắc: không có nhu cầu nào ở đây
        // cần nó.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }

    private List<String> splitOrigins() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (origins.isEmpty()) {
            // Không có origin nào được khai báo thì chặn hết, thay vì mặc định
            // mở cho tất cả.
            return List.of();
        }
        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS chứa '*'. Không được phép: kết hợp với "
                            + "credentials sẽ cho phép mọi trang web gọi API. "
                            + "Hãy liệt kê từng domain thật.");
        }
        return origins;
    }
}
