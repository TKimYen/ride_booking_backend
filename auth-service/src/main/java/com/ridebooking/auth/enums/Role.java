package com.ridebooking.auth.enums;

/**
 * Quyền của tài khoản, khớp với cột {@code users.role} trong db.md
 * (GUEST: khách chưa đăng nhập, USER: hành khách đã đăng nhập, ADMIN: quản trị viên).
 */
public enum Role {
    GUEST,
    USER,
    ADMIN
}