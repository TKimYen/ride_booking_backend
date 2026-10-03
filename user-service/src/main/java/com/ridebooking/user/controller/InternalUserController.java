package com.ridebooking.user.controller;

import com.ridebooking.user.dto.BatchProfilesRequest;
import com.ridebooking.user.dto.CreateProfileRequest;
import com.ridebooking.user.dto.UserResponse;
import com.ridebooking.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nội bộ giữa các service — KHÔNG dùng từ frontend.
 *
 * <p>Bảo vệ bằng header {@code X-Internal-Key} thay vì JWT vì auth-service gọi
 * lúc user mới đăng ký, chưa có token nào được client giữ. Khoá đặt qua
 * {@code app.internal.key} và phải trùng với {@code app.clients.user-service.internal-key}
 * của auth-service. Trong Docker Compose cả hai lấy chung biến {@code INTERNAL_API_KEY}.
 */
@RestController
@RequestMapping("/api/internal/users")
@SecurityRequirements
@Tag(name = "0. Internal (service ↔ service)",
        description = "Chỉ auth-service gọi được, cần header X-Internal-Key đúng")
public class InternalUserController {

    private final UserService userService;
    private final String internalKey;

    public InternalUserController(UserService userService,
                                  @org.springframework.beans.factory.annotation.Value("${app.internal.key}") String internalKey) {
        this.userService = userService;
        this.internalKey = internalKey;
    }

    @Operation(summary = "Tạo / cập nhật hồ sơ (idempotent)",
            description = "Gọi bởi auth-service khi đăng ký hoặc admin tạo tài khoản.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã tạo hoặc đã cập nhật hồ sơ"),
            @ApiResponse(responseCode = "400", description = "Body không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Thiếu hoặc sai X-Internal-Key")})
    @PostMapping
    public ResponseEntity<UserResponse> upsert(@RequestHeader(value = "X-Internal-Key", required = false) String key,
                                                @Valid @RequestBody CreateProfileRequest request) {
        if (!internalKey.equals(key)) {
            throw new org.springframework.security.access.AccessDeniedException("Internal key không hợp lệ");
        }
        return ResponseEntity.ok(userService.upsertProfile(request));
    }

    @Operation(summary = "Lấy hồ sơ theo id", description = "auth-service dùng để ghép response đăng nhập / đăng ký.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "403", description = "Sai X-Internal-Key"),
            @ApiResponse(responseCode = "404", description = "Chưa có hồ sơ")})
    @PostMapping("/{userId}")
    public ResponseEntity<UserResponse> getById(@RequestHeader(value = "X-Internal-Key", required = false) String key,
                                                @PathVariable Integer userId) {
        if (!internalKey.equals(key)) {
            throw new org.springframework.security.access.AccessDeniedException("Internal key không hợp lệ");
        }
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @Operation(summary = "Lấy nhiều hồ sơ trong một lần gọi",
            description = """
                    Dùng bởi booking-service khi hiển thị danh sách đánh giá: mỗi đánh giá
                    thuộc một người khác nhau, gọi từng id sẽ tốn N request cho một trang.

                    Id không có hồ sơ bị bỏ qua, không trả về null — nên danh sách trả về
                    có thể ngắn hơn `userIds` đã gửi.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "400", description = "userIds rỗng hoặc quá 200 id"),
            @ApiResponse(responseCode = "403", description = "Sai X-Internal-Key")})
    @PostMapping("/batch")
    public ResponseEntity<java.util.List<UserResponse>> getProfiles(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @Valid @RequestBody BatchProfilesRequest request) {
        if (!internalKey.equals(key)) {
            throw new org.springframework.security.access.AccessDeniedException("Internal key không hợp lệ");
        }
        return ResponseEntity.ok(userService.getProfiles(request.userIds()));
    }
}
