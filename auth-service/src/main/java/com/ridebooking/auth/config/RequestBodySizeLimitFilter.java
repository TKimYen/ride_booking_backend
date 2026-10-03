package com.ridebooking.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chặn request body vượt ngưỡng, trả 413 Payload Too Large.
 *
 * <p>Vì sao phải tự làm thay vì tin Tomcat: {@code server.tomcat.max-http-form-post-size}
 * chỉ áp cho {@code application/x-www-form-urlencoded}. Body {@code application/json}
 * được đọc thẳng qua {@link ServletInputStream} nên Tomcat không giới hạn gì —
 * đã thử gửi 20MB JSON và server đọc trọn vào heap rồi mới validate. Với
 * {@code @Size(max = 255)} trên DTO, việc validate chỉ chạy <em>sau khi</em> body
 * đã nằm trong RAM, nên nó không chống được DoS, chỉ trả lỗi muộn.
 *
 * <p>Chặn hai tầng vì chỉ kiểm {@code Content-Length} là không đủ:
 * <ol>
 *   <li>Client gửi {@code Content-Length} vượt ngưỡng → trả 413 ngay, chưa đọc byte nào.</li>
 *   <li>Client dùng {@code Transfer-Encoding: chunked} (không có Content-Length), hoặc
 *       khai {@code Content-Length} nhỏ hơn thực tế → bọc stream đếm byte thật và ném
 *       ngay khi vượt ngưỡng. Không đọc hết body của kẻ tấn công.</li>
 * </ol>
 *
 * <p>Chạy với {@code HIGHEST_PRECEDENCE} nên nằm trước Spring Security: request
 * chưa xác thực cũng bị chặn, không tốn công BCrypt hay truy vấn DB.
 *
 * <p>Filter tự ghi {@link #TOO_LARGE_ATTRIBUTE} rồi ném {@link PayloadTooLargeException}.
 * Jackson bọc IOException thành {@code HttpMessageNotReadableException}, nên
 * {@code GlobalExceptionHandler} đọc attribute này để trả đúng 413 thay vì 400.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    /**
     * Attribute đánh dấu request đã vượt ngưỡng. Đặt tên đủ lạ để không đụng
     * attribute của Spring/Tomcat.
     */
    public static final String TOO_LARGE_ATTRIBUTE = RequestBodySizeLimitFilter.class.getName() + ".TOO_LARGE";

    private final int maxBodyBytes;

    public RequestBodySizeLimitFilter(@Value("${app.request.max-body-bytes:65536}") int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (request.getContentLengthLong() > maxBodyBytes) {
            reject(response);
            return;
        }

        chain.doFilter(new LimitedBodyRequest(request), response);
    }

    /** Trả 413 luôn, không chạm vào body. Dùng cho tầng chặn theo Content-Length. */
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":413,\"error\":\"Payload Too Large\",\"message\":\"Kích thước "
                + "request tối đa là " + maxBodyBytes + " byte\"}");
    }

    /** Bọc request để giới hạn số byte đọc thật từ body. */
    private final class LimitedBodyRequest extends HttpServletRequestWrapper {

        // Giữ tham chiếu gốc: getRequest() của wrapper khai báo trả về ServletRequest
        // nên phải ép kiểu, mà ép kiểu ở đây là code thừa.
        private final HttpServletRequest original;

        private LimitedBodyRequest(HttpServletRequest request) {
            super(request);
            this.original = request;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CountingServletInputStream(super.getInputStream(), maxBodyBytes, original);
        }
    }

    /**
     * Đếm byte đã đọc, ném {@link PayloadTooLargeException} khi vượt ngưỡng.
     *
     * <p>Cần cả {@link #read()} và {@link #read(byte[], int, int)}: Jackson thường
     * đọc theo mảng, nhưng một số đường đọc từng byte. Bỏ sót nhánh nào là lỗ hổng.
     */
    private static final class CountingServletInputStream extends ServletInputStream {

        private final ServletInputStream source;
        private final int maxBytes;
        private final HttpServletRequest request;
        private long consumed;

        private CountingServletInputStream(ServletInputStream source, int maxBytes, HttpServletRequest request) {
            this.source = source;
            this.maxBytes = maxBytes;
            this.request = request;
        }

        @Override
        public int read() throws IOException {
            int value = source.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = source.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(long delta) throws PayloadTooLargeException {
            consumed += delta;
            if (consumed > maxBytes) {
                request.setAttribute(TOO_LARGE_ATTRIBUTE, Boolean.TRUE);
                throw new PayloadTooLargeException(maxBytes, consumed);
            }
        }

        @Override
        public boolean isFinished() {
            return source.isFinished();
        }

        @Override
        public boolean isReady() {
            return source.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            source.setReadListener(readListener);
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }

    /** Đánh dấu body vượt ngưỡng khi đang đọc. */
    static class PayloadTooLargeException extends IOException {

        private static final long serialVersionUID = 1L;

        PayloadTooLargeException(int maxBytes, long consumed) {
            super("Request body vượt giới hạn " + maxBytes + " byte (đã đọc " + consumed + " byte)");
        }
    }
}
