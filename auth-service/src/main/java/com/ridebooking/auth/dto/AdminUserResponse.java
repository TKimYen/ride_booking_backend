package com.ridebooking.auth.dto;

import com.ridebooking.auth.enums.Role;
import java.time.LocalDate;

public record AdminUserResponse(
        Integer id,
        String fullName,
        String phone,
        String email,
        LocalDate dob,
        String gender,
        Role role,
        String createdAt,
        boolean emailVerified,
        String lastLoginAt,
        boolean locked) {
}
