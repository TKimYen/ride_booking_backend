package com.ridebooking.gateway.config;

import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Service phía sau gateway không phản hồi thì trả 503, không phải 500.
 *
 * <p>Đây không phải chuyện thẩm mỹ. Client phân biệt hai loại lỗi hoàn toàn khác
 * nhau: 500 là "server hỏng code, không thử lại được", 503 là "service tạm thời
 * không có, thử lại sau". Frontend xử lý mỗi loại khác nhau — 500 thì hiện lỗi và
 * dừng, 503 thì tự retry. Trả nhầm 500 làm một sự cố hạ tầng tạm thời biến thành
 * lỗi không tự phục hồi.
 *
 * <p>Đã đo được trước khi viết: khi {@code booking-service} chưa tồn tại,
 * {@code GET /api/trips} trả <b>500</b> kèm stack trace
 * {@code ConnectException: Connection refused} trong log gateway.
 */
@Component
@Order(-1)
public class UpstreamUnavailableFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(UpstreamUnavailableFilter::isUpstreamUnreachable,
                        error -> respondServiceUnavailable(exchange, error));
    }

    /**
     * Phân biệt "không gọi được service" với "service trả lỗi nghiệp vụ".
     *
     * <p>Chỉ bắt lỗi ở tầng kết nối. Điều này không chỉ để giữ lỗi nghiệp vụ:
     * bất kỳ lỗi nào sinh ra TRONG gateway (một filter khác ném, sai cấu hình
     * filter chain) cũng không được biến thành 503 — 503 nói "service chưa sẵn
     * sàng, thử lại", còn lỗi nội bộ gateway là lỗi cần sửa code.
     *
     * <p>Ghi chú: lỗi 4xx/5xx trả về từ service KHÔNG đi qua đây. Spring Cloud
     * Gateway chuyển tiếp status + body của upstream như một response bình
     * thường, nên {@code onErrorResume} không bao giờ thấy. Vì vậy phần lớn giá
     * trị của hàm này là phân loại lỗi phát sinh bên trong gateway — xem
     * {@code UpstreamUnavailableFilterClassificationTest}.
     */
    static boolean isUpstreamUnreachable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException          // không có listener
                    || cause instanceof ConnectTimeoutException  // hết giờ chờ kết nối
                    || cause instanceof TimeoutException) {   // hết giờ đọc/ghi
                return true;
            }
            if (cause instanceof NotFoundException
                    || cause instanceof ResponseStatusException) {
                // Service đã trả lỗi có chủ đích — không phải sự cố mạng.
                return false;
            }
        }
        return false;
    }

    private static Mono<Void> respondServiceUnavailable(ServerWebExchange exchange,
                                                        Throwable error) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(error);
        }
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String message = """
                {"status":503,"error":"Service Unavailable",\
                "message":"Dich vu tam thoi khong san sang. Vui long thu lai sau.","path":"%s"}"""
                .formatted(exchange.getRequest().getPath().value());

        DataBuffer body = response.bufferFactory()
                .wrap(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }
}
