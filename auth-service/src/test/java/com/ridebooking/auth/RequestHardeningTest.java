package com.ridebooking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.auth.repository.LoginAttemptRepository;
import com.ridebooking.auth.repository.RefreshTokenRepository;
import com.ridebooking.auth.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test cho 4 điểm lộ ra khi review: key JSON trùng, body không giới hạn,
 * sai Content-Type bị trả 500, và email dính khoảng trắng.
 *
 * <p>Chạy trên H2, không cần Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Siết chặt đầu vào HTTP")
class RequestHardeningTest {

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

    @Value("${app.request.max-body-bytes:65536}")
    private int maxBodyBytes;

    /**
     * Kích thước body dùng để thử vượt ngưỡng — CỐ ĐỊNH, không lấy từ
     * {@link #maxBodyBytes}. Nếu lấy theo giá trị đang cấu hình thì nâng ngưỡng
     * lên sẽ khiến payload to theo, request vẫn bị chặn và test vẫn xanh: hỏng
     * mà không phát hiện ra.
     */
    private static final int OVERSIZE_BODY = 256 * 1024;

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
    @DisplayName("JSON có key email trùng bị từ chối, không tạo user")
    void duplicateJsonKeyRejected() throws Exception {
        String body = """
                {"fullName":"Dup","email":"first1@test.com","password":"Huy@2002x",\
                "email":"second1@test.com"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        // Quan trọng: phải CHẶN, chứ không phải "tạo user cho giá trị cuối".
        assertThat(userRepository.findByEmailIgnoreCase("first1@test.com")).isEmpty();
        assertThat(userRepository.findByEmailIgnoreCase("second1@test.com")).isEmpty();
    }

    @Test
    @DisplayName("Key trùng ở field không phải email cũng bị từ chối")
    void duplicateJsonKeyOnAnyFieldRejected() throws Exception {
        String body = """
                {"fullName":"A","fullName":"B","email":"dupname1@test.com","password":"Huy@2002x"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmailIgnoreCase("dupname1@test.com")).isEmpty();
    }

    @Test
    @DisplayName("Không đụng tới key lặp do chính server sinh ra trong JSON hợp lệ")
    void validJsonStillWorks() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Hop Le","email":"hople1@test.com","password":"Huy@2002x"}"""))
                .andExpect(status().isCreated());
    }


    @Test
    @DisplayName("Ngưỡng cấu hình nhỏ hơn payload thử nghiệm, nếu không test là vô nghĩa")
    void configuredLimitIsBelowTestPayload() {
        assertThat(maxBodyBytes)
                .as("nếu ngưỡng >= payload thì các test 413 bên dưới không còn ý nghĩa")
                .isLessThan(OVERSIZE_BODY);
    }

    @Test
    @DisplayName("Body vượt ngưỡng trả 413 chứ không phải 400/500")
    void oversizedBodyRejected() throws Exception {
        String huge = "A".repeat(OVERSIZE_BODY);
        String body = """
                {"fullName":"%s","email":"big1@test.com","password":"Huy@2002x"}""".formatted(huge);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge());

        assertThat(userRepository.findByEmailIgnoreCase("big1@test.com")).isEmpty();
    }

    @Test
    @DisplayName("Body chunked không có Content-Length cũng bị chặn")
    void oversizedChunkedBodyRejected() throws Exception {
        // Nếu chỉ kiểm Content-Length thì request này lọt qua vì không có header đó.
        String huge = "A".repeat(OVERSIZE_BODY);
        String body = """
                {"fullName":"%s","email":"chunked1@test.com","password":"Huy@2002x"}""".formatted(huge);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        // header này ép framework đọc body theo kiểu chunked
                        .header("Transfer-Encoding", "chunked")
                        .content(body))
                .andExpect(status().isPayloadTooLarge());

        assertThat(userRepository.findByEmailIgnoreCase("chunked1@test.com")).isEmpty();
    }

    @Test
    @DisplayName("Body vừa phải vẫn qua được")
    void bodyUnderLimitAccepted() throws Exception {
        String body = """
                {"fullName":"Vua Phai","email":"underlimit1@test.com","password":"Huy@2002x"}""";

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }


    @Test
    @DisplayName("Sai Content-Type trả 415 chứ không phải 500")
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN)
                        .content("{\"email\":\"x@test.com\",\"password\":\"y\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }


    @Test
    @DisplayName("Email dính khoảng trắng hai bên vẫn đăng ký được và được lưu đã trim")
    void emailIsTrimmedBeforeValidation() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Space","email":"  spaceprobe1@test.com  ","password":"Huy@2002x"}"""))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmailIgnoreCase("spaceprobe1@test.com")).isPresent();
    }

    @Test
    @DisplayName("Đăng nhập bằng email dính khoảng trắng vẫn vào được")
    void loginTrimsEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Space","email":"loginspace1@test.com","password":"Huy@2002x"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"  loginspace1@test.com  ","password":"Huy@2002x"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Khoảng trắng trong mật khẩu KHÔNG bị trim âm thầm")
    void passwordIsNotTrimmed() throws Exception {
        // Đăng ký với mật khẩu có khoảng trắng ở đầu.
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Pw","email":"pwspace1@test.com","password":" Huy@2002x"}"""))
                .andExpect(status().isCreated());

        // Nếu server tự trim, đăng nhập bằng bản đã trim sẽ thành công -> sai.
        // Đúng ra phải thất bại vì mật khẩu thật là " Huy@2002x" khác "Huy@2002x".
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pwspace1@test.com","password":"Huy@2002x"}"""))
                .andExpect(status().isUnauthorized());

        // Còn bản đúng khoảng trắng thì phải vào được.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pwspace1@test.com","password":" Huy@2002x"}"""))
                .andExpect(status().isOk());
    }


    @Test
    @DisplayName("Token trong body trả về sub là email đã chuẩn hoá")
    void issuedTokenUsesNormalizedEmail() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Norm","email":"  MiXeD@Test.COM ","password":"Huy@2002x"}"""))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String subject = objectMapper.readTree(body).get("accessToken").asText();
        String payload = new String(Base64.getUrlDecoder().decode(subject.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(payload).contains("mixed@test.com");
    }
}
