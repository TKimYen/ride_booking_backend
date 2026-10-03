package com.ridebooking.user.controller;

import com.ridebooking.user.dto.PageResponse;
import com.ridebooking.user.dto.UpdateProfileRequest;
import com.ridebooking.user.dto.UserResponse;
import com.ridebooking.user.security.AuthenticatedUser;
import com.ridebooking.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhóm User - API cho Trang Thông tin tài khoản.
 *
 * <p>Đăng xuất nằm ở auth-service ({@code POST /api/auth/logout}) vì chỉ
 * auth-service mới nắm được refresh token.
 */
@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "1. Hồ sơ cá nhân", description = "Trang Thông tin tài khoản: xem, sửa hồ sơ, tra cứu người dùng")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Hồ sơ của tôi",
            description = """
                    Dùng cho header và Trang Thông tin tài khoản.
                    Nếu hồ sơ chưa tồn tại (đăng ký lúc user-service sập) sẽ tự tạo từ claim trong JWT.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn")})
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.ensureProfile(
                principal.id(), principal.email(), principal.fullName(), principal.role()));
    }

    @Operation(summary = "Cập nhật hồ sơ của tôi",
            description = "Chỉ cập nhật Họ tên / SĐT / Ngày sinh / Giới tính. Muốn đổi email hoặc mật khẩu thì gọi sang auth-service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Chưa có hồ sơ")})
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(principal.id(), request));
    }

    @Operation(summary = "Xem hồ sơ người khác",
            description = """
                    Admin và chính mình xem đầy đủ. Người dùng thường chỉ thấy họ tên và email đã che
                    (`h*******@gmail.com`); SĐT / ngày sinh / giới tính trả về `null` và `masked=true`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công (có thể đã che dữ liệu nhạy cảm)"),
            @ApiResponse(responseCode = "404", description = "Chưa có hồ sơ")})
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable Integer id) {
        return ResponseEntity.ok(userService.getByIdAs(id, principal.id(), principal.isAdmin()));
    }

    @Operation(summary = "Danh sách người dùng phân trang",
            description = """
                    Module khác dùng để hiển thị tên tài xế / chủ xe.
                    Quyền ADMIN xem đầy đủ; user thường xem bản đã che dữ liệu nhạy cảm.
                    Xem trang Quản lý thì dùng nhóm tag Admin.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")})
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Parameter(description = "Từ khoá: họ tên, email hoặc SĐT. Bỏ trống lấy tất cả")
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.search(keyword, page, size, principal.isAdmin()));
    }
}