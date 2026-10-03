package com.ridebooking.auth.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tiện ích đọc principal hiện tại từ SecurityContext. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user ? Optional.of(user) : Optional.empty();
    }

    public static Integer currentUserIdOrThrow() {
        return currentUser().map(AuthenticatedUser::id).orElseThrow(
                () -> new IllegalStateException("Không tìm thấy người dùng đang đăng nhập"));
    }

    public static boolean isAdmin() {
        return currentUser().map(AuthenticatedUser::isAdmin).orElse(false);
    }
}