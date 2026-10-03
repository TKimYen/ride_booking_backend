package com.ridebooking.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật hạ tầng {@code @Scheduled} cho job trả ghế hết hạn.
 *
 * <p>Tách khỏi class chính để test có thể tắt job qua
 * {@code app.booking.expiry-job.enabled=false} mà không phải thay đổi gì ở đây.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
