package com.ridebooking.auth.controller;

import com.ridebooking.auth.dto.AuthResponse;
import com.ridebooking.auth.dto.ChangeEmailRequest;
import com.ridebooking.auth.dto.ChangePasswordRequest;
import com.ridebooking.auth.dto.ConfirmEmailChangeRequest;
import com.ridebooking.auth.dto.EmailResponse;
import com.ridebooking.auth.dto.EmailTokenRequest;
import com.ridebooking.auth.dto.EmailVerifiedResponse;
import com.ridebooking.auth.dto.ForgotPasswordRequest;
import com.ridebooking.auth.dto.LoginRequest;
import com.ridebooking.auth.dto.RefreshTokenRequest;
import com.ridebooking.auth.dto.RegisterRequest;
import com.ridebooking.auth.dto.ResetPasswordRequest;
import com.ridebooking.auth.dto.UserResponse;
import com.ridebooking.auth.security.AuthenticatedUser;
import com.ridebooking.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Luồng xác thực. Quy ước token của dự án:
 *
 * <ul>
 *   <li><b>accessToken</b> — JWT sống 15 phút, gửi kèm mọi request: {@code Authorization: Bearer <token>}</li>
 *   <li><b>refreshToken</b> — chuỗi ngẫu nhiên sống 7 ngày, đổi lấy accessToken mới
 *       qua {@code POST /api/auth/refresh}. Mỗi lần đổi thì token cũ bị thu hồi (xoay vòng).</li>
 * </ul>
 *
 * <p>Client nên giữ refreshToken ở nơi an toàn hơn accessToken (httpOnly cookie
 * nếu có backend render, hoặc storage riêng) và tự gọi {@code /refresh} khi
 * nhận 401.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Xác thực", description = "Đăng ký, đăng nhập, refresh token, JWT, đổi mật khẩu")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Đăng ký tài khoản mới",
            description = """
                    Khách tự tạo tài khoản, role luôn là `USER`.
                    Họ tên / SĐT / ngày sinh / giới tính được chuyển sang user-service tạo hồ sơ.
                    Trả về cả accessToken và refreshToken.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đăng ký thành công, kèm cặp token"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (thiếu trường, sai định dạng, mật khẩu yếu)",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "409", description = "Email đã tồn tại")})
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, httpRequest.getHeader("User-Agent"), clientIp(httpRequest)));
    }

    @Operation(
            summary = "Đăng nhập",
            description = """
                    Trả về accessToken (15') + refreshToken (7 ngày).
                    Sai từ `max-failed-attempts` lần liên tiếp thì tài khoản bị khoá `lock-minutes` phút.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công, kèm cặp token"),
            @ApiResponse(responseCode = "401", description = "Email hoặc mật khẩu không đúng"),
            @ApiResponse(responseCode = "423", description = "Tài khoản đang bị khoá do đăng nhập sai nhiều lần")})
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest.getHeader("User-Agent"), clientIp(httpRequest)));
    }

    @Operation(
            summary = "Đổi cặp token (xoay vòng refresh token)",
            description = """
                    Gửi refreshToken nhận được lúc đăng nhập, nhận lại accessToken mới.
                    Refresh token cũ bị thu hồi ngay — nếu client cũ vẫn dùng lại token đã thu hồi,
                    server coi đó là dấu hiệu token bị đánh cắp và **thu hồi toàn bộ phiên** của tài khoản.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đổi token thành công"),
            @ApiResponse(responseCode = "400", description = "Thiếu refreshToken"),
            @ApiResponse(responseCode = "401", description = "Refresh token sai, hết hạn, đã thu hồi hoặc bị tái sử dụng"),
            @ApiResponse(responseCode = "423", description = "Tài khoản đang bị khoá")})
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken(),
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest)));
    }

    @Operation(
            summary = "Đăng xuất",
            description = """
                    Gửi refreshToken để chấm dứt đúng phiên đó.
                    Không gửi refreshToken (hoặc gửi rỗng) thì đăng xuất **toàn bộ thiết bị**.
                    Access token đã cấp vẫn sống tới hết 15 phút — frontend cần xoá nó khỏi storage.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã đăng xuất"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")})
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(request == null ? null : request.refreshToken(), principal.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Thông tin tài khoản của tôi",
            description = "Dùng cho header và Trang Thông tin tài khoản. Phần hồ sơ được ghép từ user-service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn")})
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(authService.getProfile(principal.id()));
    }

    @Operation(summary = "Đổi mật khẩu", description = """
            Trang Thông tin tài khoản. Cần mật khẩu hiện tại.
            Sau khi đổi, **mọi phiên đăng nhập khác bị thu hồi** (kể cả refresh token vừa cấp),
            nên client phải đăng nhập lại.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu mới không đủ mạnh"),
            @ApiResponse(responseCode = "401", description = "Mật khẩu hiện tại không đúng hoặc trùng mật khẩu cũ")})
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.id(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kiểm tra token",
            description = "Trả 204 nếu token còn hiệu lực, 401 nếu không. Dùng cho api-gateway và service khác.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Token hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Token không hợp lệ / hết hạn")})
    @GetMapping("/validate")
    public ResponseEntity<Void> validate() {
        return ResponseEntity.noContent().build();
    }


    @Operation(summary = "Xác minh địa chỉ email",
            description = "Mở link trong email xác minh. Mã dùng một lần, hết hạn sau 24 giờ. "
                    + "Xác minh xong tài khoản cũng được mở khoá khỏi bộ đếm đăng nhập sai.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xác minh thành công"),
            @ApiResponse(responseCode = "400", description = "Mã không hợp lệ, đã dùng hoặc hết hạn")})
    @PostMapping("/verify-email")
    public ResponseEntity<EmailVerifiedResponse> verifyEmail(
            @Valid @RequestBody EmailTokenRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.token()));
    }

    @Operation(summary = "Gửi lại email xác minh",
            description = "Dùng khi người dùng không thấy email xác minh. "
                    + "Luôn trả 200 với cùng một thông điệp, kể cả khi email không tồn tại, "
                    + "để không lộ danh sách email đã đăng ký.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã xử lý"),
            @ApiResponse(responseCode = "429", description = "Quá nhiều lần gửi trong ngắn hạn")})
    @PostMapping("/resend-verification")
    public ResponseEntity<EmailResponse> resendVerification(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        String message = authService.resendVerification(request.email(), clientIp(http));
        return ResponseEntity.ok(EmailResponse.of(message));
    }

    @Operation(summary = "Quên mật khẩu",
            description = "Gửi link đặt lại mật khẩu. Chỉ gửi cho tài khoản đã xác minh email. "
                    + "Luôn trả 200 với cùng một thông điệp để không lộ email nào có trong hệ thống.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã xử lý"),
            @ApiResponse(responseCode = "429", description = "Quá nhiều lần gửi trong ngắn hạn")})
    @PostMapping("/forgot-password")
    public ResponseEntity<EmailResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        String message = authService.forgotPassword(request.email(), clientIp(http));
        return ResponseEntity.ok(EmailResponse.of(message));
    }

    @Operation(summary = "Đặt lại mật khẩu",
            description = "Dùng link trong email. Sau khi đổi mật khẩu, toàn bộ phiên đăng nhập "
                    + "khác bị thu hồi và tài khoản được mở khoá.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Mã không hợp lệ, đã dùng, hết hạn, hoặc mật khẩu mới quá yếu")})
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Đổi địa chỉ email",
            description = "Gửi link xác nhận tới EMAIL MỚI và gửi cảnh báo tới EMAIL CŨ. "
                    + "Thay đổi chỉ có hiệu lực khi mở link ở email mới và nhập mật khẩu hiện tại. "
                    + "Sau khi đổi, mọi phiên đăng nhập bị thu hồi.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Đã gửi link xác nhận"),
            @ApiResponse(responseCode = "400", description = "Email mới không dùng được"),
            @ApiResponse(responseCode = "401", description = "Mật khẩu hiện tại không đúng")})
    @PostMapping("/change-email")
    public ResponseEntity<EmailResponse> changeEmail(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody ChangeEmailRequest request,
                                                     HttpServletRequest http) {
        authService.changeEmail(principal.id(), request, clientIp(http));
        return ResponseEntity.accepted()
                .body(EmailResponse.of("Đã gửi link xác nhận tới email mới. "
                        + "Chúng tôi cũng đã gửi cảnh báo tới email hiện tại của bạn."));
    }

    @Operation(summary = "Xác nhận đổi email",
            description = "Mở link gửi tới email mới và nhập mật khẩu hiện tại để hoàn tất đổi email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đổi email thành công"),
            @ApiResponse(responseCode = "400", description = "Mã không hợp lệ / email mới đã có người dùng"),
            @ApiResponse(responseCode = "401", description = "Mật khẩu hiện tại không đúng")})
    @PostMapping("/confirm-email-change")
    public ResponseEntity<EmailVerifiedResponse> confirmEmailChange(
            @Valid @RequestBody ConfirmEmailChangeRequest request) {
        return ResponseEntity.ok(authService.confirmEmailChange(request));
    }

    /**
     * IP thật của client, dùng cho rate limit.
     *
     * <p>KHÔNG tự đọc {@code X-Forwarded-For}. Header đó do client kiểm soát, và
     * auth-service expose cổng 8081 ra ngoài — nếu tin nó thì kẻ tấn công chỉ cần
     * thêm header là lách được mọi giới hạn theo IP.
     *
     * <p>Thay vào đó dựa vào {@code server.forward-headers-strategy=native}: Tomcat
     * tự phân giải chuỗi X-Forwarded-For nhưng chỉ khi request đến từ proxy được
     * tin cậy. Gateway đã ghi đè (không nối thêm) XFF nên giá trị đó không thể bị
     * giả mạo từ phía client.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}