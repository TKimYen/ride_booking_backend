package com.ridebooking.auth.client;

import static com.ridebooking.auth.client.UserServiceClientProperties.CreateProfileRequest;
import static com.ridebooking.auth.client.UserServiceClientProperties.ProfileView;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Gọi sang user-service để tạo / đọc hồ sơ.
 *
 * <p>Vì auth_db và user_db đã tách riêng, đăng ký ở auth-service không tự sinh
 * hồ sơ được — cần gọi service kia. Nếu user-service lỗi, hàm chỉ log cảnh báo
 * và trả về {@code null}; hồ sơ sẽ được user-service tự tạo lazy ở lần truy cập
 * đầu tiên (xem {@code UserService#ensureProfile}).
 *
 * <p>Endpoint {@code /api/internal/**} không dùng JWT vì lúc đăng ký client chưa
 * có token, nên request này gắn {@code X-Internal-Key} do hai service cùng đọc
 * từ biến môi trường {@code INTERNAL_API_KEY}.
 */
@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestTemplate restTemplate;
    private final String internalKey;

    public UserServiceClient(RestTemplateBuilder builder, UserServiceClientProperties properties) {
        this.restTemplate = builder
                .rootUri(properties.getBaseUrl())
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .build();
        this.internalKey = properties.getInternalKey();
    }

    /** Tạo hồ sơ tại user-service. Trả về {@code null} nếu user-service không khả dụng. */
    public ProfileView createProfile(CreateProfileRequest request) {
        try {
            return restTemplate.exchange(
                    "/api/internal/users",
                    HttpMethod.POST,
                    new HttpEntity<>(request, internalHeaders()),
                    ProfileView.class).getBody();
        } catch (Exception ex) {
            log.warn("Không tạo được hồ sơ cho userId={} trên user-service: {}",
                    request.userId(), ex.getMessage());
            return null;
        }
    }

    /** Lấy hồ sơ từ user-service để ghép vào response của auth-service. */
    public ProfileView fetchProfile(Integer userId) {
        try {
            return restTemplate.exchange(
                    "/api/internal/users/" + userId,
                    HttpMethod.POST,
                    new HttpEntity<>(internalHeaders()),
                    ProfileView.class).getBody();
        } catch (Exception ex) {
            log.warn("Không đọc được hồ sơ userId={} từ user-service: {}", userId, ex.getMessage());
            return null;
        }
    }

    /**
     * Đổi email trong bản sao hồ sơ bên user-service.
     *
     * <p>Endpoint {@code /api/internal/users} là upsotent nên gửi lại toàn bộ hồ
     * sơ lấy được, chỉ thay email — tránh ghi đè mất SĐT / ngày sinh của người
     * dùng. Trả về {@code false} nếu user-service không khả dụng; khi đó bản sao
     * sẽ được đồng bộ lại ở lần đăng nhập sau.
     */
    public boolean updateEmail(Integer userId, String newEmail) {
        try {
            // Phải nằm trong try: fetchProfile trả null khi user-service lỗi,
            // gọi ngoài try sẽ ném NullPointerException ra khỏi luồng đổi email.
            ProfileView current = fetchProfile(userId);
            if (current == null) {
                log.warn("Bỏ qua cập nhật email hồ sơ userId={}: không đọc được hồ sơ hiện tại. "
                        + "Bản sao sẽ tự được sửa ở lần user mở GET /api/users/me", userId);
                return false;
            }

            ProfileView updated = restTemplate.exchange(
                    "/api/internal/users",
                    HttpMethod.POST,
                    new HttpEntity<>(new CreateProfileRequest(userId, current.fullName(), newEmail,
                            current.phone(), current.dob(), current.gender(), current.role()),
                            internalHeaders()),
                    ProfileView.class).getBody();

            // Không tin vào HTTP 200: phải xem lại cả email mà user-service thật
            // sự ghi lại. Trước đây log "đã cập nhật" chỉ dựa vào HTTP 200 nên đã
            // báo thành công trong khi user-service vẫn giữ email cũ.
            if (updated == null || !newEmail.equals(updated.email())) {
                log.warn("user-service trả 200 nhưng email hồ sơ userId={} vẫn chưa đổi "
                        + "(nhận về: {})", userId, updated == null ? null : updated.email());
                return false;
            }
            log.info("Đã cập nhật email hồ sơ tại user-service cho userId={}", userId);
            return true;
        } catch (Exception ex) {
            log.warn("Không cập nhật được email hồ sơ cho userId={} trên user-service: {}",
                    userId, ex.getMessage());
            return false;
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Key", internalKey);
        return headers;
    }
}