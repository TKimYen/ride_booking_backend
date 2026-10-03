package com.ridebooking.auth.entity;

import com.ridebooking.auth.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Bảng {@code users} trong database {@code auth_db} — chỉ chứa thông tin định danh
 * và thông tin đăng nhập (tách từ bảng Users gốc trong db.md).
 *
 * <p>Phần hồ sơ cá nhân (họ tên, SĐT, ngày sinh, giới tính) do user-service sở hữu
 * trong database {@code user_db}, bảng {@code user_profiles}.
 *
 * <p>Trường {@code full_name} được giữ lại ở đây vì token JWT mang tên hiển thị và
 * Trang Chọn chỗ & Thanh toán cần auto-fill ngay sau khi đăng nhập — tránh gọi
 * thêm service khác. Nguồn sự thật của hồ sơ vẫn là user-service.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Email đã được xác minh qua link gửi vào hộp thư chưa.
     *
     * <p>Đăng nhập vẫn cho phép khi chưa xác minh (xem
     * {@code app.email.require-verified-for-login}); cờ này chủ yếu quyết định
     * có được dùng tính năng khôi phục mật khẩu hay không — nếu ai đó đăng ký
     * email của người khác rồi bấm "quên mật khẩu" thì sẽ chiếm được tài khoản.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (role == null) {
            role = Role.USER;
        }
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public LocalDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(LocalDateTime emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    /** Không bao giờ trả password hash ra ngoài. */
    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + "', role=" + role + '}';
    }
}