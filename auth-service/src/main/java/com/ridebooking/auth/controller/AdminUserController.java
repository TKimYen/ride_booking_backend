package com.ridebooking.auth.controller;

import com.ridebooking.auth.dto.CreateUserRequest;
import com.ridebooking.auth.dto.UserResponse;
import com.ridebooking.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhóm Admin - quản lý tài khoản. Yêu cầu role ADMIN (chặn ở SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "2. Admin - Người dùng", description = "Quản lý tài khoản, chỉ ADMIN được gọi")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Danh sách tài khoản",
            description = "Không phân trang. Muốn có phân trang dùng `GET /api/admin/users` của user-service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền ADMIN")})
    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(authService.listUsers());
    }

    @Operation(summary = "Admin tạo tài khoản",
            description = "Tạo trực tiếp trong auth_db, đồng thời tạo hồ sơ rỗng ở user-service.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền ADMIN"),
            @ApiResponse(responseCode = "409", description = "Email đã tồn tại")})
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(201).body(authService.createUser(request));
    }

    @Operation(summary = "Gỡ khoá tài khoản",
            description = "Dùng khi người dùng bị khoá vì đăng nhập sai quá nhiều lần và đã hết thời gian chờ.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã gỡ khoá (hoặc tài khoản vốn không bị khoá)"),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền ADMIN"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản")})
    @PatchMapping("/{id}/unlock")
    public ResponseEntity<UserResponse> unlock(@PathVariable Integer id) {
        return ResponseEntity.ok(authService.unlockAccount(id));
    }
}