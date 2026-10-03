package com.ridebooking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridebooking.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cùng bộ siết chặt đầu vào với auth-service. Phải có ở cả hai service: chỉ siết
 * một bên là không đủ, vì request đi tới service nào tuỳ endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Siết chặt đầu vào HTTP (user-service)")
class RequestHardeningTest {

    private static final String KEY_HEADER = "X-Internal-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Value("${app.internal.key}")
    private String internalKey;

    @Value("${app.request.max-body-bytes:65536}")
    private int maxBodyBytes;

    /** Cố định, không lấy từ ngưỡng cấu hình — xem giải thích ở bản auth-service. */
    private static final int OVERSIZE_BODY = 256 * 1024;

    @BeforeEach
    void setUp() {
        userProfileRepository.deleteAll();
    }

    /** Repo chưa có finder theo email, nên dò thẳng danh sách đang lưu. */
    private boolean hasProfile(String email) {
        return userProfileRepository.findAll().stream()
                .anyMatch(profile -> email.equalsIgnoreCase(profile.getEmail()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder internalPost(String body) {
        return post("/api/internal/users").contentType(MediaType.APPLICATION_JSON).header(KEY_HEADER, internalKey)
                .content(body);
    }

    @Test
    @DisplayName("Ngưỡng cấu hình nhỏ hơn payload thử nghiệm")
    void configuredLimitIsBelowTestPayload() {
        assertThat(maxBodyBytes).isLessThan(OVERSIZE_BODY);
    }

    @Test
    @DisplayName("JSON có key trùng bị từ chối")
    void duplicateJsonKeyRejected() throws Exception {
        mockMvc.perform(internalPost("""
                {"fullName":"Dup","email":"dupu1@test.com","password":"Huy@2002x",\
                "email":"dupu2@test.com"}"""))
                .andExpect(status().isBadRequest());

        assertThat(hasProfile("dupu1@test.com")).isFalse();
        assertThat(hasProfile("dupu2@test.com")).isFalse();
    }

    @Test
    @DisplayName("Body vượt ngưỡng trả 413")
    void oversizedBodyRejected() throws Exception {
        String huge = "A".repeat(OVERSIZE_BODY);
        mockMvc.perform(internalPost("""
                {"fullName":"%s","email":"bigu1@test.com"}""".formatted(huge)))
                .andExpect(status().isPayloadTooLarge());

        assertThat(hasProfile("bigu1@test.com")).isFalse();
    }

    @Test
    @DisplayName("Sai Content-Type trả 415 chứ không phải 500")
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/internal/users").contentType(MediaType.TEXT_PLAIN)
                        .header(KEY_HEADER, internalKey).content("{\"email\":\"x@test.com\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("Email dính khoảng trắng vẫn qua được validate")
    void emailIsTrimmedBeforeValidation() throws Exception {
        mockMvc.perform(internalPost("""
                {"fullName":"Space","email":"  userprobe1@test.com  ","userId":9001}"""))
                .andExpect(status().is2xxSuccessful());

        assertThat(hasProfile("userprobe1@test.com")).isTrue();
    }
}
