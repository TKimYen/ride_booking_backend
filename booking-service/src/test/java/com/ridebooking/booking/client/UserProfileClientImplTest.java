package com.ridebooking.booking.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.hamcrest.Matchers;
import org.springframework.web.client.RestTemplate;

/**
 * Client lấy tên người đánh giá.
 *
 * <p>Hai điều kiện quan trọng hơn phần "lấy đúng tên":
 * <ul>
 *   <li>user-service chết thì Trang đánh giá KHÔNG được trả lỗi 500;</li>
 *   <li>id đã xem thì không gọi lại, vì một trang 10 đánh giá mà 10 người khác nhau
 *       sẽ thành 10 lần gọi HTTP cho mỗi lần mở trang.</li>
 * </ul>
 */
class UserProfileClientImplTest {

    private MockRestServiceServer server;
    private UserProfileClientImpl client;

    @BeforeEach
    void setUp() {
        // Giữ nguyên cách ghép baseUrl + đường dẫn tương đối như lúc chạy thật:
        // nếu client ghép sai (ví dụ gọi nguyên đường dẫn tuyệt đối) thì test bắt được.
        RestTemplate restTemplate = new RestTemplateBuilder()
                .rootUri("http://user-service.test")
                .build();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new UserProfileClientImpl(restTemplate, "test-key", 300L, 100);
    }

    @Test
    @DisplayName("Trả về map id -> tên, và bỏ qua id không có hồ sơ")
    void returnsNamesByUserId() {
        server.expect(requestTo("http://user-service.test/api/internal/users/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", "test-key"))
                .andRespond(withSuccess(
                        "[{\"id\":1,\"fullName\":\"Nguyễn A\"},{\"id\":2,\"fullName\":\"Nguyễn B\"}]",
                        MediaType.APPLICATION_JSON));

        Map<Integer, String> names = client.fetchDisplayNames(List.of(1, 2, 999));

        assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of(1, "Nguyễn A", 2, "Nguyễn B"));
        server.verify();
    }

    @Test
    @DisplayName("Id đã xem trong TTL thì không gọi lại user-service")
    void secondCallForSameUsersHitsCache() {
        server.expect(requestTo("http://user-service.test/api/internal/users/batch"))
                .andRespond(withSuccess("[{\"id\":1,\"fullName\":\"Nguyễn A\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDisplayNames(List.of(1))).containsEntry(1, "Nguyễn A");
        // Lần hai KHÔNG được gọi HTTP. Không khai expect thứ hai nên mọi request đều
        // bị MockRestServiceServer coi là thừa và test đỏ.
        assertThat(client.fetchDisplayNames(List.of(1))).containsEntry(1, "Nguyễn A");
        server.verify();
    }

    @Test
    @DisplayName("Chỉ gọi cho id chưa có trong cache")
    void onlyFetchesUncachedUsers() {
        server.expect(requestTo("http://user-service.test/api/internal/users/batch"))
                .andExpect(content().string(Matchers.containsString("\"userIds\":[1]")))
                .andRespond(withSuccess("[{\"id\":1,\"fullName\":\"Nguyễn A\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://user-service.test/api/internal/users/batch"))
                .andExpect(content().string(Matchers.containsString("\"userIds\":[2]")))
                .andRespond(withSuccess("[{\"id\":2,\"fullName\":\"Nguyễn B\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDisplayNames(List.of(1))).containsEntry(1, "Nguyễn A");
        assertThat(client.fetchDisplayNames(List.of(1, 2))).containsEntry(2, "Nguyễn B");
        server.verify();
    }

    @Test
    @DisplayName("user-service lỗi thì trả map rỗng chứ không ném lỗi ra")
    void degradesGracefullyWhenUserServiceFails() {
        server.expect(requestTo("http://user-service.test/api/internal/users/batch"))
                .andRespond(withServerError());

        assertThat(client.fetchDisplayNames(List.of(1, 2))).isEmpty();
    }

    @Test
    @DisplayName("Tên rỗng thì không đưa vào cache, để lần sau hỏi lại được")
    void blankNameIsNotCached() {
        server.expect(requestTo("http://user-service.test/api/internal/users/batch"))
                .andRespond(withSuccess("[{\"id\":1,\"fullName\":\"  \"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://user-service.test/api/internal/users/batch"))
                .andRespond(withSuccess("[{\"id\":1,\"fullName\":\"Nguyễn A\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDisplayNames(List.of(1))).isEmpty();
        assertThat(client.fetchDisplayNames(List.of(1))).containsEntry(1, "Nguyễn A");
        server.verify();
    }

    @Test
    @DisplayName("Danh sách rỗng thì không gọi HTTP")
    void emptyInputSkipsHttpCall() {
        assertThat(client.fetchDisplayNames(List.of())).isEmpty();
        assertThat(client.fetchDisplayNames(null)).isEmpty();
        server.verify();
    }
}