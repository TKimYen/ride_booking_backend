package com.ridebooking.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.StreamReadFeature;

/**
 * Bảo vệ việc test ĐỌC ĐƯỢC cấu hình đang chạy thật.
 *
 * <p>Đã xảy ra sự cố này: {@code src/test/resources/application.properties} trùng
 * tên với file main, nên trên classpath file test che mất file main. Test vẫn xanh
 * vì code dùng giá trị mặc định, còn {@code spring.jackson.*} bị bỏ trống và không
 * ai hay biết. Hậu quả: test báo 201 cho JSON key trùng trong khi stack thật trả
 * 400 — test xanh nhưng kiểm tra sai thứ.
 *
 * <p>Đây là test cho chính cái nguyên nhân đó: nếu ai đó gộp cấu hình siết chặt
 * trở lại vào {@code application.properties} của main, test này đỏ.
 */
@SpringBootTest
@DisplayName("Cấu hình hardening phải được nạp trong test")
class HardeningConfigLoadedTest {

    @Autowired
    private JacksonProperties jacksonProperties;

    @Autowired
    private Environment environment;

    @Value("${app.request.max-body-bytes:65536}")
    private int maxBodyBytes;

    @MockitoBean
    private com.ridebooking.auth.client.UserServiceClient userServiceClient;

    @Test
    @DisplayName("Chặn JSON key trùng thật sự được bind (không phải để mặc định rỗng)")
    void strictDuplicateDetectionIsBound() {
        assertThat(jacksonProperties.getRead())
                .as("spring.jackson.read phải có STRICT_DUPLICATE_DETECTION=true trong test")
                .containsEntry(StreamReadFeature.STRICT_DUPLICATE_DETECTION, Boolean.TRUE);
    }

    @Test
    @DisplayName("File hardening được nạp qua spring.config.import")
    void hardeningFileIsImported() {
        assertThat(environment.getProperty("app.request.max-body-bytes"))
                .as("phải đọc được ngưỡng body từ application-hardening.properties")
                .isNotNull();
    }

    @Test
    @DisplayName("Giới hạn Tomcat có mặt, không phụ thuộc mặc định framework")
    void tomcatLimitsAreExplicit() {
        assertThat(environment.getProperty("server.tomcat.max-swallow-size")).isNotNull();
        assertThat(environment.getProperty("server.tomcat.max-http-form-post-size")).isNotNull();
        assertThat(environment.getProperty("server.max-http-request-header-size")).isNotNull();
    }

    @Test
    @DisplayName("Ngưỡng body là số dương hợp lý, không phải mặc định 2MB của Tomcat")
    void bodyLimitIsSane() {
        assertThat(maxBodyBytes).isPositive();
        assertThat(maxBodyBytes)
                .as("ngưỡng phải nhỏ hơn nhiều so với 2MB, vì JSON không hề bị Tomcat giới hạn")
                .isLessThan(2 * 1024 * 1024);
    }
}
