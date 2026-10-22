package com.ridebooking.booking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.booking.controller.BookingController;
import com.ridebooking.booking.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Bảo mật cho booking-service.
 *
 * <p>Khác user-service ở chỗ: phần lớn endpoint catalog phải mở cho khách
 * chưa đăng nhập. Theo requirement.md, Nhóm 1 (Guest) gồm Trang chủ và Trang
 * Danh sách kết quả. Khách vãng lai còn đặt vé được không cần tài khoản
 * (db.md B8): quyền với đơn chứng minh bằng header {@code X-Guest-Token}.
 *
 * <p>Nguyên tắc: {@code permitAll} CHỈ áp cho phương thức GET trên đường dẫn
 * catalog. Mọi thao tác ghi đều đòi {@code ADMIN}. Ràng buộc này viết bằng
 * {@code HttpMethod} tường minh chứ không dựa vào thứ tự rule, để sau này thêm
 * endpoint mới không vô tình mở công khai.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,
                          CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.corsProperties = corsProperties;
    }

    /** Đường dẫn catalog mà khách chưa đăng nhập được phép đọc. */
    private static final List<String> PUBLIC_CATALOG_PATHS = List.of(
            "/api/locations/**",
            "/api/bus-companies/**",
            "/api/routes/**",
            "/api/trips/**",
            "/api/seats/**",
            "/api/stop-points/**",
            "/api/reviews/**");

    /** Request vào một đơn cụ thể có kèm header token khách vãng lai. */
    private static final RequestMatcher GUEST_BOOKING_REQUEST = new AndRequestMatcher(
            request -> StringUtils.hasText(request.getHeader(BookingController.GUEST_TOKEN_HEADER)),
            new OrRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/bookings/*"),
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/bookings/*/pay"),
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/bookings/*/cancel")));

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()

                        // Không qua JWT: webhook Stripe không có người dùng. Controller
                        // tự kiểm header X-Internal-Key và ném AccessDeniedException (403).
                        // Gateway cố ý KHÔNG route /api/internal/** nên không lộ ra ngoài.
                        .requestMatchers("/api/internal/**").permitAll()

                        // Chỉ GET. POST/PUT/PATCH/DELETE rơi xuống rule
                        // hasRole("ADMIN") ở dưới.
                        .requestMatchers(HttpMethod.GET, PUBLIC_CATALOG_PATHS.toArray(String[]::new)).permitAll()

                        // requirement.md giao Trang Quản lý Chuyến xe cho Thành viên 4.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Khách vãng lai đặt vé được (db.md B8). "Đơn của tôi" và gắn
                        // vé vào tài khoản thì luôn cần JWT; khai TRƯỚC rule permitAll.
                        .requestMatchers(HttpMethod.GET, "/api/bookings").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/bookings/claim").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/bookings", "/api/bookings/lookup").permitAll()
                        // Xem/thanh toán/huỷ một đơn: mở khi có X-Guest-Token, service tự
                        // so token với hash. Không có header thì vẫn đòi JWT -> 401 rõ ràng.
                        .requestMatchers(GUEST_BOOKING_REQUEST).permitAll()
                        .requestMatchers("/api/bookings/**").authenticated()

                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED,
                                        "Bạn chưa đăng nhập hoặc token không hợp lệ", request.getRequestURI()))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN,
                                        "Bạn không có quyền truy cập tài nguyên này", request.getRequestURI())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Danh sách origin cụ thể, không phải "*" - tránh lộ API sang domain khác.
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Internal-Key", "X-Forwarded-For",
                BookingController.GUEST_TOKEN_HEADER));
        configuration.setExposedHeaders(List.of("Location", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(HttpServletResponse response,
                            HttpStatus status,
                            String message,
                            String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}