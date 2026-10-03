package com.ridebooking.auth.client;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình kết nối tới user-service, dùng khi auth-service cần đồng bộ hồ sơ
 * (đăng ký / đổi email / admin tạo user).
 */
@ConfigurationProperties(prefix = "app.clients.user-service")
public class UserServiceClientProperties {

    /** Địa chỉ gốc của user-service. */
    private String baseUrl = "http://localhost:8082";

    private int connectTimeoutMillis = 3000;

    private int readTimeoutMillis = 5000;

    /** Khoá gửi qua header X-Internal-Key, phải khớp app.internal.key của user-service. */
    private String internalKey = "ride-booking-internal-dev-key";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public String getInternalKey() {
        return internalKey;
    }

    public void setInternalKey(String internalKey) {
        this.internalKey = internalKey;
    }

    /** Dữ liệu hồ sơ tối thiểu cần tạo sang user-service. */
    public record CreateProfileRequest(Integer userId, String fullName, String email, String phone,
                                      LocalDate dob, String gender, String role) {
    }

    /** Kết quả trả về từ user-service. */
    public record ProfileView(Integer id, String fullName, String phone, String email,
                              LocalDate dob, String gender, String role, String createdAt) {
    }
}