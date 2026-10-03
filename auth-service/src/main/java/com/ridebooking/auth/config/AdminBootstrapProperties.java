package com.ridebooking.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tài khoản admin tạo sẵn khi khởi động auth-service.
 *
 * <p>Cần thiết vì {@code POST /api/admin/users} chỉ chấp nhận token ADMIN, tức là
 * một database rỗng sẽ không có đường nào để tạo tài khoản admin đầu tiên. Biến
 * môi trường {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} giải quyết chỗ này.
 *
 * <p>Để trống {@code email} thì không bootstrap gì cả (dùng cho môi trường thật
 * nơi admin được tạo tay qua migration hoặc SQL).
 */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public class AdminBootstrapProperties {

    /** Email tài khoản admin. Để trống = không bootstrap. */
    private String email = "";

    /** Mật khẩu tài khoản admin. Không có mặc định, phải tự đặt. */
    private String password = "";

    private String fullName = "Quản trị viên";

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isEnabled() {
        return email != null && !email.isBlank();
    }
}