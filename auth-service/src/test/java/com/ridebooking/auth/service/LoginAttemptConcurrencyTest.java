package com.ridebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ridebooking.auth.entity.LoginAttempt;
import com.ridebooking.auth.repository.LoginAttemptRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Nhiều request đăng nhập sai CÙNG LÚC cho một cặp (email, IP).
 *
 * <p>Đây là trường hợp mà người đọc code dễ bỏ sót: cả N thread đều chạy
 * {@code findByEmailAndIpAddress} trước khi có ai ghi được dòng nào, nên cả N đều
 * thấy "không có" rồi cùng INSERT. Ràng buộc {@code UNIQUE (email, ip_address)}
 * chặn việc tạo hai dòng, nhưng nếu không xử lý lỗi thì:
 *
 * <ul>
 *   <li>client nhận {@code 500} thay vì {@code 401} — lộ lỗi nội bộ;
 *   <li>bộ đếm mất đúng những lần ghi bị loại, tức là bộ đếm đếm thiếu.
 * </ul>
 *
 * <p>Chạy trên H2 với repository thật nên ràng buộc UNIQUE và đường retry được
 * kiểm chứng thật chứ không phải mock.
 *
 * <p>NHƯNG H2 không nâng unique violation lên tới transaction cha giống PostgreSQL,
 * nên bản chạy mặc định KHÔNG đủ để kết luận lỗi P0 đã hết. Muốn kiểm chứng trên
 * PostgreSQL thật thì trỏ test vào một database rác rồi chạy:
 *
 * <pre>
 * docker compose exec -T auth-db psql -U postgres -c "CREATE DATABASE auth_race;"
 * AUTH_TEST_PG_URL=jdbc:postgresql://localhost:5432/auth_race \
 * AUTH_TEST_PG_USER=postgres AUTH_TEST_PG_PASSWORD=postgres \
 *   mvn -pl auth-service test -Dtest=LoginAttemptConcurrencyTest
 * </pre>
 */
@SpringBootTest
@DisplayName("Ghi bộ đếm khi nhiều request đăng nhập sai chạy đồng thời")
class LoginAttemptConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @MockitoBean
    private com.ridebooking.auth.client.UserServiceClient userServiceClient;

    /**
     * Mặc định (không set biến môi trường) test chạy trên H2 như trước. Khi
     * AUTH_TEST_PG_URL được set, test trỏ sang PostgreSQL thật để tái hiện đúng
     * hành vi gây ra P0: unique violation phải làm cả transaction ngoài rollback.
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String url = System.getenv("AUTH_TEST_PG_URL");
        if (url == null || url.isBlank()) {
            return;
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("AUTH_TEST_PG_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("AUTH_TEST_PG_PASSWORD", "postgres"));
    }

    @BeforeEach
    void reset() {
        loginAttemptRepository.deleteAll();
    }

    @Test
    @DisplayName("Không mất lần đếm và không ném exception ra ngoài")
    void registerFailure_concurrentSamePair() throws Exception {
        String email = "race@test.com";
        String ip = "198.51.100.7";

        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    // Cùng đợn để mọi thread chen vào đúng khoảnh thời gian đọc
                    startGun.await();
                    loginAttemptService.registerFailure(email, ip);
                    return null;
                }));
            }
            startGun.countDown();

            for (Future<?> future : futures) {
                assertThatCode(future::get)
                        .as("request đồng thời không được phép văng exception")
                        .doesNotThrowAnyException();
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        List<LoginAttempt> rows = loginAttemptRepository.findAll();
        assertThat(rows)
                .as("mỗi cặp (email, IP) chỉ được có MỘT dòng")
                .hasSize(1);

        System.out.println("[INFO] race test chạy trên DB: " + environment.getProperty("spring.datasource.url")
                + " | driver: " + environment.getProperty("spring.datasource.driver-class-name"));

        LoginAttempt row = rows.get(0);
        assertThat(row.getEmail()).isEqualTo(email);
        assertThat(row.getIpAddress()).isEqualTo(ip);
        assertThat(row.getFailedCount())
                .as("phải đếm đủ %d lần, không bị mất do va chạm INSERT", THREADS)
                .isEqualTo(THREADS);
    }

    @Test
    @DisplayName("Cặp khác IP vẫn đếm riêng, không đè lên nhau")
    void registerFailure_concurrentDifferentIps() throws Exception {
        String email = "race2@test.com";

        int ips = 4;
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(ips);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < ips; i++) {
                String ip = "198.51.100." + (10 + i);
                futures.add(pool.submit(() -> {
                    startGun.await();
                    loginAttemptService.registerFailure(email, ip);
                    return null;
                }));
            }
            startGun.countDown();
            for (Future<?> future : futures) {
                assertThatCode(future::get).doesNotThrowAnyException();
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(loginAttemptRepository.findAll())
                .as("mỗi IP một dòng riêng")
                .hasSize(ips);
        assertThat(loginAttemptRepository.findAll())
                .allSatisfy(row -> assertThat(row.getFailedCount()).isEqualTo(1));
    }
}
