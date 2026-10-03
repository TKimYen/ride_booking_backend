package com.ridebooking.auth.bootstrap;

import com.ridebooking.auth.service.EmailNormalizer;
import com.ridebooking.auth.client.UserServiceClient;
import com.ridebooking.auth.client.UserServiceClientProperties;
import com.ridebooking.auth.config.AdminBootstrapProperties;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.enums.Role;
import com.ridebooking.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo (hoặc nâng lên) một tài khoản ADMIN khi auth-service khởi động, dựa trên
 * biến môi trường {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}.
 *
 * <p>Không có cơ chế này thì database rỗng sẽ không tạo được admin đầu tiên,
 * vì mọi API admin đều đòi token ADMIN.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /**
     * Mật khẩu admin mặc định xuất hiện trong {@code docker-compose.yml} và
     * {@code .env.example}. Chỉ dùng cho local; production phải đặt lại.
     */
    private static final java.util.Set<String> DEFAULT_PASSWORDS = java.util.Set.of(
            "Admin@12345", "admin12345", "Admin@123456", "admin@12345", "Admin@1234");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;
    private final UserServiceClient userServiceClient;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AdminBootstrapProperties properties,
                          UserServiceClient userServiceClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.userServiceClient = userServiceClient;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Bỏ qua bootstrap admin: không đặt app.bootstrap.admin.email");
            return;
        }

        String email = EmailNormalizer.normalize(properties.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setFullName(properties.getFullName());
            user.setPassword(passwordEncoder.encode(requirePassword()));
            user.setRole(Role.ADMIN);
            userRepository.save(user);
            log.warn("Đã tạo tài khoản ADMIN: {}. "
                    + "ĐỔI MẬT KHẨU NGAY sau khi đăng nhập lần đầu", email);
            warnIfWeakPassword(email);
            createProfile(user);
            return;
        }

        if (user.getRole() == Role.ADMIN) {
            log.info("Tài khoản admin {} đã tồn tại, không thay đổi", email);
            // Vẫn gọi: lần trước có thể user-service chưa sẵn sàng, hoặc hồ sơ
            // bị xoá, nên admin có thể đăng nhập được nhưng không hiện trong danh
            // sách. Idempotent nên gọi mỗi lần khởi động cũng không sao.
            createProfile(user);
            return;
        }

        log.warn("Nâng quyền tài khoản {} lên ADMIN theo cấu hình bootstrap", email);
        user.setRole(Role.ADMIN);
        userRepository.save(user);
        createProfile(user);
    }

    /**
     * Tạo hồ sơ ở user-service cho admin.
     *
     * <p>Không chặn khởi động nếu thất bại: lúc auth-service vừa lên thì
     * user-service có thể chưa sẵn sàng. Hồ sơ sẽ tự được tạo ở lần admin đầu
     * tiên gọi {@code GET /api/users/me} (xem {@code UserService#ensureProfile}).
     */
    private void createProfile(User admin) {
        try {
            userServiceClient.createProfile(
                    new UserServiceClientProperties.CreateProfileRequest(
                            admin.getId(), admin.getFullName(), admin.getEmail(),
                            null, null, null, admin.getRole().name()));
            log.info("Đã tạo hồ sơ cho admin {} ở user-service", admin.getEmail());
        } catch (RuntimeException e) {
            log.warn("Chưa tạo được hồ sơ cho admin {} (user-service chưa sẵn sàng?): {}. "
                    + "Sẽ tự tạo ở lần admin gọi GET /api/users/me",
                    admin.getEmail(), e.getMessage());
        }
    }

    private String requirePassword() {
        String password = properties.getPassword();
        if (password == null || password.length() < 8) {
            throw new IllegalStateException(
                    "Phải đặt app.bootstrap.admin.password dài ít nhất 8 ký tự để tạo tài khoản admin");
        }
        return password;
    }

    /**
     * Cảnh báo khi mật khẩu admin là giá trị mặc định nằm trong docker-compose /
     * .env.example. Ở môi trường thật, admin là tài khoản quyền cao nhất nên
     * mật khẩu mặc định là lỗ hổng trực tiếp; cần đổi qua ADMIN_PASSWORD.
     */
    private void warnIfWeakPassword(String email) {
        String password = properties.getPassword();
        if (password == null) {
            return;
        }
        boolean weak = DEFAULT_PASSWORDS.contains(password.trim());
        if (weak) {
            log.warn("""

                    ==================== CẢNH BÁO BẢO MẬT ====================
                    Tài khoản admin {} vừa được tạo với MẬT KHẨU MẶC ĐỊNH.
                    Không được dùng mật khẩu này khi deploy thật.
                    Hãy đặt ADMIN_PASSWORD trong file .env, hoặc bỏ trống
                    ADMIN_EMAIL để tạo admin bằng tay.
                    =============================================================
                    """, email);
        } else {
            log.info("Mật khẩu admin không phải giá trị mặc định, OK");
        }
    }
}