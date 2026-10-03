package com.ridebooking.booking.security;

import com.ridebooking.booking.enums.Role;
import java.util.List;

/** Principal đã xác thực, đọc từ JWT do auth-service cấp. */
public record AuthenticatedUser(Integer id, String email, String fullName, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public List<String> authorities() {
        return List.of("ROLE_" + role.name());
    }
}