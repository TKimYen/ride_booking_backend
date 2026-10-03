package com.ridebooking.auth.security;

import com.ridebooking.auth.enums.Role;
import java.util.List;

/**
 * Principal đã xác thực, đọc từ JWT.
 * Dùng chung shape với user-service để gateway/service khác parse token giống nhau.
 */
public record AuthenticatedUser(Integer id, String email, String fullName, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public List<String> authorities() {
        return List.of("ROLE_" + role.name());
    }
}