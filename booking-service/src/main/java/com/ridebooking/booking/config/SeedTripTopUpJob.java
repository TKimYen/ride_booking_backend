package com.ridebooking.booking.config;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bù chuyến mẫu cho những ngày tới còn thiếu, CHỈ dùng ở dev/staging.
 *
 * <p>Dữ liệu mẫu chỉ có chuyến trong {@code SEED_TRIP_DAYS} ngày kể từ lúc nạp.
 * Không có bước này thì vài ngày sau trang tìm kiếm trống trơn, và cách chữa duy
 * nhất là xoá database để seed lại.
 *
 * <p>Bật bằng {@code SEED_TRIP_TOPUP=true}. Mặc định TẮT và production không được
 * bật: ở đó chuyến do admin tạo, tự sinh chuyến là bán vé cho chuyến không có thật.
 * Biến này tách riêng khỏi {@code SEED_ENABLED} vì production vẫn có thể cần nạp
 * danh mục mẫu một lần.
 *
 * <p>Chạy một lần lúc khởi động (sau {@link SeedDataLoader}) và mỗi ngày theo
 * {@code SEED_TRIP_TOPUP_CRON}, để server chạy liên tục nhiều ngày vẫn còn chuyến.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed.trip-topup.enabled", havingValue = "true")
public class SeedTripTopUpJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedTripTopUpJob.class);

    private final SeedTripGenerator generator;

    public SeedTripTopUpJob(SeedTripGenerator generator) {
        this.generator = generator;
    }

    @Override
    public void run(ApplicationArguments args) {
        topUp();
    }

    @Scheduled(cron = "${app.seed.trip-topup.cron:0 5 0 * * *}")
    public void topUp() {
        SeedTripGenerator.TopUpResult result = generator.topUpAll(LocalDate.now());
        if (result.trips() > 0) {
            log.info("Bù chuyến mẫu {} ngày tới: thêm {} chuyến, {} ghế",
                    generator.tripDays(), result.trips(), result.seats());
        } else {
            log.info("Bù chuyến mẫu: {} ngày tới đã đủ chuyến", generator.tripDays());
        }
    }
}
