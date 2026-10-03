package com.ridebooking.payment.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient gọi booking-service.
 *
 * <p>Base URL và header {@code X-Internal-Key} khai đúng một chỗ. Timeout ngắn
 * vì đây là phụ thuộc trong luồng thanh toán: booking-service treo thì webhook
 * cũng không được treo theo, Stripe sẽ tự gửi lại.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient bookingRestClient(RestClient.Builder builder, BookingClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));

        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-Internal-Key", properties.getInternalKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }
}
