package com.ridebooking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Ngược lại với {@link UpstreamUnavailableFilterTest}: khi service CÒN SỐNG và trả
 * lỗi nghiệp vụ, gateway phải giữ nguyên lỗi đó.
 *
 * <p>Đây là cách bẫy dễ dính khi viết filter "trả 503 cho mọi lỗi": nó làm mất
 * thông điệp có giá trị như "ghế đã có người giữ" hoặc "email đã tồn tại", và
 * client không phân biệt được lỗi nghiệp vụ với sự cố hạ tầng.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Gateway giữ nguyên lỗi nghiệp vụ từ service còn sống")
class UpstreamBusinessErrorPassthroughTest {

    /** Service giả, trả lỗi nghiệp vụ có chủ đích. */
    private static HttpServer fakeBookingService;
    private static int fakePort;

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void pointGatewayAtFakeService(DynamicPropertyRegistry registry) {
        registry.add("BOOKING_SERVICE_URI", () -> "http://localhost:" + fakePort);
    }

    @BeforeAll
    static void startFakeService() throws IOException {
        fakeBookingService = HttpServer.create(new InetSocketAddress(0), 0);
        fakePort = fakeBookingService.getAddress().getPort();

        fakeBookingService.createContext("/api/trips/seats", exchange -> {
            byte[] payload = """
                    {"status":409,"message":"Ghe da co nguoi giu","path":"/api/seats"}"""
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });

        fakeBookingService.createContext("/api/trips/boom", exchange -> {
            byte[] payload = """
                    {"status":500,"message":"Loi noi bo cua booking-service"}"""
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });

        fakeBookingService.start();
    }

    @AfterAll
    static void stopFakeService() {
        if (fakeBookingService != null) {
            fakeBookingService.stop(0);
        }
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("Lỗi 409 từ service được giữ nguyên, kèm thông điệp")
    void conflictFromUpstreamIsPreserved() {
        String body = client().get().uri("/api/trips/seats")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(body)
                .as("thông điệp nghiệp vụ là thứ client cần để hiển thị cho người dùng; "
                        + "bị filter nuốt thành 503 thì frontend chỉ còn báo 'tạm thời "
                        + "không có' và người dùng không hiểu phải làm gì")
                .contains("Ghe da co nguoi giu");
    }

    @Test
    @DisplayName("Lỗi 500 CỐ Ý từ service không bị đổi thành 503")
    void deliberateServerErrorIsNotMasked() {
        String body = client().get().uri("/api/trips/boom")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(body)
                .as("service đã trả 500 vì lỗi code thật; đổi thành 503 là che mất "
                        + "tín hiệu cần cảnh báo, và client sẽ thử lại vô ích")
                .contains("Loi noi bo cua booking-service");
    }
}
