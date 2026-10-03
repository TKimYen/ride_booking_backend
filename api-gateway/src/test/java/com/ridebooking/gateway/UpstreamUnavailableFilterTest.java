package com.ridebooking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;

/**
 * Phân biệt "service chết" với "service trả lỗi".
 *
 * <p>Đây là hợp đồng mà frontend dựa vào để quyết định có tự thử lại hay không:
 * 503 nghĩa là hạ tầng tạm thời không có, 4xx/5xx có chủ đích nghĩa là có vấn đề
 * thật cần nhìn vào log. Trả nhầm 500 làm một sự cố khởi động lại đơn giản biến
 * thành lỗi không tự phục hồi.
 *
 * <p>Dùng WebTestClient chứ không dùng TestRestTemplate: gateway là WebFlux, và
 * TestRestTemplate đã bị gỡ khỏi Boot 4.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Gateway trả 503 khi service phía sau không phản hồi")
class UpstreamUnavailableFilterTest {

    /**
     * Tự dựng WebTestClient thay vì dùng {@code @AutoConfigureWebTestClient} /
     * {@code @LocalServerPort}: trong Boot 4 chúng nằm ở module
     * spring-boot-webflux-test, mà pom của gateway không kéo vào — kéo cả chỉ để
     * lấy một bean test thì không đáng.
     */
    /**
     * Trỏ mọi service phía sau về một cổng chắc chắn không có listener.
     *
     * <p>Để mặc định {@code localhost:8083/8084} thì test chỉ xanh khi máy lập
     * trình viên chưa chạy gì, và đỏ lúc đang có booking-service chạy thật — tôi
     * dính đúng vụ đó (gateway trả 200 vì 8083 còn sống). Test hạ tầng phải tự tạo
     * được tình huống "service chết", không hy vọng vào trạng thái máy.
     *
     * <p>Cổng lấy từ {@link ServerSocket#new()} rồi đóng lại, và phải là cổng
     * không đặc quyền: nối tới cổng dưới 1024 trên máy này trả về
     * {@code SocketException: Permission denied}, khác {@code ConnectException}
     * mà filter bắt, nên gateway trả 500 thay vì 503 và test thất bại vì lý do
     * hoàn toàn không liên quan tới thứ nó đang kiểm.
     */
    @DynamicPropertySource
    static void pointRoutesAtDeadUpstream(DynamicPropertyRegistry registry) {
        registry.add("BOOKING_SERVICE_URI", () -> "http://127.0.0.1:" + deadPort());
        registry.add("PAYMENT_SERVICE_URI", () -> "http://127.0.0.1:" + deadPort());
    }

    /** Cổng trống: bind để giành một cổng ngẫu nhiên, rồi nhả ra cho gateway. */
    private static int deadPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không dò được cổng trống cho test", ex);
        }
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** Cổng ngẫu nhiên do Spring Boot chọn: nằm ngoài 1-1023 nên không đụng dịch vụ
     *  thật đang chạy, và chắc chắn chưa có listener. */
    @Value("${local.server.port}")
    private int port;

    @Test
    @DisplayName("Service chết -> 503, không phải 500")
    void deadUpstreamReturns503() {
        client().get().uri("/api/trips")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Upstream đã bị trỏ về cổng trống bởi deadPort(), nên luôn đúng tình huống
        // "service chết" bất kể máy đang chạy gì.
    }

    @Test
    @DisplayName("Body lỗi đủ trạng thái, thông điệp và path để frontend hiển thị")
    void errorBodyIsActionable() {
        client().get().uri("/api/payments")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.message")
                .value(message -> assertThat((String) message)
                        .as("thiếu message thì frontend chỉ hiện status trống")
                        .contains("Dich vu tam thoi khong san sang"))
                .jsonPath("$.path").isEqualTo("/api/payments");
    }

    @Test
    @DisplayName("Route không tồn tại -> 404, KHÔNG phải 503")
    void unknownRouteReturns404() {
        client().get().uri("/api/khong-ton-tai")
                .exchange()
                // 503 nói service chưa sẵn sàng, 404 nói endpoint không tồn tại.
                // Trộn hai loại làm client thử lại một URL vốn không bao giờ có.
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Tài liệu OpenAPI vẫn phục vụ bình thường (không phải 503)")
    void openApiDocsStillWork() {
        client().get().uri("/auth/v3/api-docs")
                .exchange()
                .expectStatus().isOk();
    }
}
