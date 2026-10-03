package com.ridebooking.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.auth.enums.Role;
import com.ridebooking.auth.repository.LoginAttemptRepository;
import com.ridebooking.auth.repository.RefreshTokenRepository;
import com.ridebooking.auth.repository.UserRepository;
import com.ridebooking.auth.security.JwtService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test đầy-cuối qua HTTP thật (MockMvc): đăng ký -> đăng nhập ->
 * refresh -> tái sử dụng token -> khoá tài khoản -> gỡ khoá -> đăng xuất.
 *
 * <p>Chạy trên H2 nên không cần Docker. user-service bị mock vì nó là service
 * riêng, đã có test riêng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Luồng xác thực end-to-end")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private com.ridebooking.auth.client.UserServiceClient userServiceClient;

    private static final String PASSWORD = "Huy@2002x";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginAttemptRepository.deleteAll();
    }

    @Test
    @DisplayName("Đăng ký trả về cả accessToken và refreshToken")
    void register_returnsBothTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Nguyễn Anh Huy",
                                "email", "huy@test.com",
                                "password", PASSWORD,
                                "phone", "0912345678"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                .andExpect(jsonPath("$.user.email").value("huy@test.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    @DisplayName("Đăng ký trùng email trả 409, mật khẩu yếu trả 400")
    void register_rejectsDuplicateAndWeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "A", "email", "a@test.com", "password", PASSWORD))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "A", "email", "a@test.com", "password", PASSWORD))))
                .andExpect(status().isConflict());

        // Không có số
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "A", "email", "b@test.com", "password", "abcdefghij"))))
                .andExpect(status().isBadRequest());

        // Không có chữ
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "A", "email", "c@test.com", "password", "12345678"))))
                .andExpect(status().isBadRequest());

        // Quá ngắn
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "A", "email", "d@test.com", "password", "Ab1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Refresh xoay vòng: token cũ hết hiệu lực, token mới dùng được")
    void refresh_rotatesToken() throws Exception {
        JsonNode login = login("huy@test.com");
        String firstRefresh = login.get("refreshToken").asText();
        String firstAccess = login.get("accessToken").asText();

        // Mỗi lần refresh sinh ra refresh token mới
        JsonNode second = postRefresh(firstRefresh);
        String secondRefresh = second.get("refreshToken").asText();
        assertNotSameToken(firstRefresh, secondRefresh);

        // Token vừa cấp dùng tiếp được, và lại bị thay bằng token mới
        JsonNode third = postRefresh(secondRefresh);
        assertNotSameToken(secondRefresh, third.get("refreshToken").asText());

        // Xoay vòng không làm phình bảng: đăng ký giữ lại token của nó, còn các
        // token từ đăng nhập/refresh đều bị thu hồi đúng lúc được dùng lại.
        Integer userId = login.get("user").get("id").asInt();
        org.assertj.core.api.Assertions.assertThat(
                        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId))
                .hasSize(2);
    }

    @Test
    @DisplayName("Dùng lại refresh token đã thu hồi bị phát hiện và khoá toàn bộ phiên")
    void refresh_reuseOfRevokedToken_revokesEverything() throws Exception {
        JsonNode login = login("huy@test.com");
        String stolen = login.get("refreshToken").asText();
        Integer userId = login.get("user").get("id").asInt();

        // Client chính đã refresh hợp lệ, nên token cũ bị thu hồi
        postRefresh(stolen);

        long activeBefore = refreshTokenRepository
                .findByUserIdAndRevokedAtIsNull(userId).size();

        // Kẻ xấu dùng lại token cũ -> toàn bộ phiên phải bị thu hồi
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", stolen))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Toàn bộ phiên")));

        // Còn phiên của client chính (đăng ký + đăng nhập đều cấp token); sau khi
        // phát hiện tái sử dụng thì không còn phiên nào còn hiệu lực
        org.assertj.core.api.Assertions.assertThat(activeBefore).isPositive();
        org.assertj.core.api.Assertions.assertThat(
                        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId))
                .isEmpty();
    }

    @Test
    @DisplayName("Sai mật khẩu 5 lần khoá cặp (email, IP), đúng mật khẩu cũng bị từ chối")
    void login_bruteForceLocksPair() throws Exception {
        ensureExists();
        for (int i = 0; i < 5; i++) {
            failLogin();
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "huy@test.com", "password", PASSWORD))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sai quá nhiều lần")))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getHeader("Retry-After"))
                        .isNotNull());
    }

    /**
     * Không khoá theo tài khoản: IP kẻ tấn công bị chặn, nhưng nạn nhân đăng nhập
     * từ IP khác vẫn phải vào được. Nếu khoá cả tài khoản thì ai cũng DoS được
     * người khác chỉ bằng cách biết trước email.
     */
    @Test
    @DisplayName("Khoá cặp ở IP này không chặn IP khác của cùng tài khoản")
    void login_lockedPairDoesNotBlockOtherIp() throws Exception {
        ensureExists();
        for (int i = 0; i < 5; i++) {
            failLogin();
        }

        // IP khác thật sự: khác client thì khác cặp (email, IP)
        mockMvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("198.51.100.42");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "huy@test.com", "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    /**
     * X-Forwarded-For do client kiểm soát nên KHÔNG được tin. Nếu tin, kẻ tấn công
     * chỉ cần thêm header là lách được mọi giới hạn theo IP.
     */
    @Test
    @DisplayName("Bỏ qua X-Forwarded-For do client tự gửi")
    void login_ignoresSpoofedForwardedForHeader() throws Exception {
        ensureExists();
        for (int i = 0; i < 5; i++) {
            failLogin();
        }

        // Khác IP client thật, chỉ đổi header -> vẫn phải bị chặn
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "198.51.100.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "huy@test.com", "password", PASSWORD))))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("Đăng nhập sai rồi đúng thì số lần sai được đặt lại")
    void login_successResetsFailureCounter() throws Exception {
        ensureExists();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "huy@test.com", "password", "sai-mat-khau"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "huy@test.com", "password", PASSWORD))))
                .andExpect(status().isOk());

        // Bộ đếm nằm ở bảng login_attempts theo cặp, không còn nằm trên user
        org.assertj.core.api.Assertions.assertThat(loginAttemptRepository.count()).isZero();
    }

    @Test
    @DisplayName("Sai 4 lần rồi đúng, sau đó sai 4 lần nữa vẫn chưa bị khoá")
    void login_counterIsConsecutiveNotCumulative() throws Exception {
        ensureExists();
        for (int i = 0; i < 4; i++) {
            failLogin();
        }
        login("huy@test.com");
        for (int i = 0; i < 4; i++) {
            failLogin();
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "huy@test.com", "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Đăng xuất thu hồi refresh token, dùng lại thì bị từ chối")
    void logout_revokesRefreshToken() throws Exception {
        JsonNode login = login("huy@test.com");
        String refresh = login.get("refreshToken").asText();
        String access = login.get("accessToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh)))
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đăng xuất không kèm token thu hồi mọi phiên của tài khoản")
    void logout_withoutTokenRevokesAllSessions() throws Exception {
        login("huy@test.com");
        JsonNode second = login("huy@test.com");
        Integer userId = second.get("user").get("id").asInt();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "")))
                        .header("Authorization", "Bearer " + second.get("accessToken").asText()))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(
                        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId))
                .isEmpty();
    }

    @Test
    @DisplayName("Đổi mật khẩu thu hồi mọi phiên, token cũ không dùng lại được")
    void changePassword_revokesSessions() throws Exception {
        JsonNode login = login("huy@test.com");
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();

        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "Huy@2002y")))
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());

        // Mật khẩu mới dùng được
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "huy@test.com", "password", "Huy@2002y"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Đổi mật khẩu sai mật khẩu cũ trả 401")
    void changePassword_wrongCurrentPassword() throws Exception {
        JsonNode login = login("huy@test.com");

        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "khong-dung", "newPassword", "Huy@2002y")))
                        .header("Authorization", "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint bảo vệ trả 401 khi thiếu hoặc sai token")
    void protectedEndpoints_requireToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/validate")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer token.gi.ao"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "khong-co-that-trong-db"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin gỡ khoá tài khoản bị khoá")
    void admin_canUnlockAccount() throws Exception {
        JsonNode login = login("huy@test.com");
        Integer userId = login.get("user").get("id").asInt();

        // Ngưỡng mặc định là 5 lần sai
        for (int i = 0; i < 5; i++) {
            failLogin();
        }

        var admin = userRepository.findByEmailIgnoreCase("huy@test.com").orElseThrow();
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        String adminToken = jwtService.generateToken(admin);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/{id}/unlock", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(loginAttemptRepository.count()).isZero();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "huy@test.com", "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("User thường không gọi được endpoint gỡ khoá của admin")
    void unlock_forbiddenForRegularUser() throws Exception {
        JsonNode login = login("huy@test.com");
        Integer userId = login.get("user").get("id").asInt();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/{id}/unlock", userId)
                        .header("Authorization", "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Refresh token trong DB chỉ lưu hash, không lưu bản gốc")
    void refreshToken_storedAsHashOnly() throws Exception {
        JsonNode login = login("huy@test.com");
        String raw = login.get("refreshToken").asText();

        var stored = refreshTokenRepository.findByTokenHash(
                refreshTokenRepository.findAll().get(0).getTokenHash()).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(stored.getTokenHash())
                .hasSize(64)
                .isNotEqualTo(raw)
                .doesNotContain(raw);
        org.assertj.core.api.Assertions.assertThat(stored.getUserId()).isPositive();
    }

    @Test
    @DisplayName("Đăng nhập trả userAgent và IP của client")
    void login_recordsDeviceInfo() throws Exception {
        ensureExists();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla/5.0 Chrome/131")
                        .with(request -> {
                            // IP do proxy tin cậy phân giải, MockMvc không chạy
                            // RemoteIpValve nên set thẳng giá trị cuối cùng
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        })
                        .content(json(Map.of("email", "huy@test.com", "password", PASSWORD))))
                .andExpect(status().isOk());

        var session = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUserAgent() != null)
                .findFirst()
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(session.getUserAgent()).contains("Chrome/131");
        org.assertj.core.api.Assertions.assertThat(session.getIpAddress()).isEqualTo("203.0.113.7");
    }


    /** Đảm bảo tài khoản tồn tại trước khi thử sai mật khẩu. */
    private void ensureExists() throws Exception {
        if (userRepository.findByEmailIgnoreCase("huy@test.com").isEmpty()) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "fullName", "Nguyễn Anh Huy", "email", "huy@test.com", "password", PASSWORD))))
                    .andExpect(status().isCreated());
        }
    }

    private void failLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "huy@test.com", "password", "sai-mat-khau"))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode login(String email) throws Exception {
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "fullName", "Nguyễn Anh Huy", "email", email, "password", PASSWORD))))
                    .andExpect(status().isCreated());
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode postRefresh(String refreshToken) throws Exception {
        String body = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * Lưu ý: hai lần refresh liên tiếp trong cùng giây có thể trả về accessToken
     * giống hệt nhau, vì claim {@code iat} của JWT chỉ chính xác tới giây và các
     * claim khác không đổi. Đó là hành vi bình thường của JWT, nên test chỉ kiểm
     * tra refresh token (random mỗi lần) là thực sự xoay vòng.
     */
    private void assertNotSameToken(String first, String second) {
        org.assertj.core.api.Assertions.assertThat(second)
                .isNotBlank()
                .isNotEqualTo(first);
    }
}