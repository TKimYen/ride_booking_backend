package com.ridebooking.user.controller;

import com.ridebooking.user.dto.PageResponse;
import com.ridebooking.user.dto.UserResponse;
import com.ridebooking.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhóm Admin - tra cứu hành khách (Quản lý Đơn hàng / Danh sách khách theo chuyến).
 * Yêu cầu role ADMIN, chặn ở SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "2. Admin - Người dùng", description = "Tra cứu hành khách, chỉ ADMIN được gọi")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Tìm hành khách",
            description = "Tìm theo họ tên, email hoặc SĐT. Trả về kèm tổng số trang để bảng phân trang ở trang Quản lý.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền ADMIN")})
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> search(
            @Parameter(description = "Từ khoá. Bỏ trống để xem tất cả")
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.search(keyword, page, size, true));
    }
}