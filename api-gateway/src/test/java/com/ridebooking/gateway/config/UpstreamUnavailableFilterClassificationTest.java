package com.ridebooking.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.ConnectTimeoutException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

/**
 * Phân loại lỗi — phần quyết định 503 hay giữ nguyên.
 *
 * <p>Test này có chủ đích chỉ kiểm tra hàm phân loại, không kiểm tra HTTP thật. Lý
 * do: lỗi 4xx/5kh từ service KHÔNG bao giờ đi qua {@code onErrorResume} (Spring
 * Cloud Gateway chuyển tiếp status + body upstream như response bình thường), nên
 * nếu chỉ test qua HTTP thì không test nào chạm tới nhánh phân loại và một bản
 * sửa "return true cho mọi thứ" sẽ không bao giờ bị phát hiện. Đó chính là điều
 * mutation test đã chứng minh: nó xanh trong khi logic đã hỏng.
 */
@DisplayName("Phân biệt lỗi mạng với lỗi nghiệp vụ")
class UpstreamUnavailableFilterClassificationTest {

    @Test
    @DisplayName("Không có listener (service chưa dựng) -> phải trả 503")
    void connectionRefusedIsUpstreamProblem() {
        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                new ConnectException("Connection refused"))).isTrue();
    }

    @Test
    @DisplayName("Hết giờ chờ kết nối -> phải trả 503")
    void connectTimeoutIsUpstreamProblem() {
        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                new ConnectTimeoutException("connect timed out"))).isTrue();
    }

    @Test
    @DisplayName("Hết giờ đọc/ghi -> phải trả 503")
    void readTimeoutIsUpstreamProblem() {
        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                new TimeoutException("Read timed out"))).isTrue();
    }

    @Test
    @DisplayName("Lỗi bọc nhiều tầng vẫn phải nhận ra")
    void wrappedNetworkErrorStillDetected() {
        // Reactor bọc lỗi kết nối trong nhiều lớp; chỉ nhìn lớp ngoài cùng sẽ
        // bỏ sót và biến sự cố mạng thành 500.
        Throwable wrapped = new IllegalStateException("reactor",
                new ConnectException("Connection refused"));

        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(wrapped)).isTrue();
    }

    @Test
    @DisplayName("Lỗi nghiệp vụ có chủ đích -> phải giữ nguyên")
    void businessErrorIsNotUpstreamProblem() {
        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                new ResponseStatusException(HttpStatus.CONFLICT, "Ghế đã có người giữ")))
                .isFalse();
    }

    @Test
    @DisplayName("415/404 do service trả -> giữ nguyên")
    void explicitStatusesAreNotUpstreamProblem() {
        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                new UnsupportedMediaTypeStatusException("application/json"))).isFalse();
        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                NotFoundException.create(true, "Khong tim thay"))).isFalse();
    }

    @Test
    @DisplayName("Lỗi nội bộ gateway KHÔNG được đổi thành 503")
    void internalGatewayErrorIsNotUpstreamProblem() {
        // Một filter khác trong chuỗi ném lỗi. 503 nói "service chưa sẵn sàng,
        // thử lại"; lỗi này là lỗi code, thử lại vô ích và che mất cảnh báo.
        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                new IllegalStateException("Filter chain misconfigured")))
                .isFalse();

        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                new NullPointerException("bug trong gateway")))
                .as("NPE là lỗi cần sửa ngay, không phải sự cố tạm thời")
                .isFalse();
    }

    @Test
    @DisplayName("IOException thường không phải lỗi kết nối upstream")
    void genericIoErrorIsNotAssumedUnreachable() {
        // IOException lạ không nên tự động quy thành "service chết" — nếu không,
        // mọi lỗi đọc file lẫn DB trong gateway đều bị báo thành 503.
        assertThat(UpstreamUnavailableFilter.isUpstreamUnreachable(
                new IOException("Không đọc được file"))).isFalse();
    }
}
