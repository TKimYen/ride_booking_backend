package com.ridebooking.booking.security;

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

    /**
     * Id người đang đăng nhập, hoặc null nếu khách vô danh.
     *
     * <p>Dùng cho API đọc vẫn muốn biết "có phải tôi không" — ví dụ danh sách đánh giá,
     * để đánh dấu đánh giá của chính người đang xem. Trả null chứ không ném lỗi vì ở
     * đó khách vô danh vẫn được xem.
     */
    public static Integer currentUserIdOrNull() {
        return currentUser().map(AuthenticatedUser::id).orElse(null);
    }

    public static Integer currentUserIdOrThrow() {
        return currentUser().map(AuthenticatedUser::id).orElseThrow(
                () -> new IllegalStateException("Không tìm thấy người dùng đang đăng nhập"));
    }

    public static boolean isAdmin() {
        return currentUser().map(AuthenticatedUser::isAdmin).orElse(false);
    }
}