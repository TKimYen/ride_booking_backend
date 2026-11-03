package com.ridebooking.booking.service;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job định kỳ đánh dấu chuyến đã đến nơi là COMPLETED, kéo theo đơn PAID. Trang
 * đánh giá dựa vào trạng thái này để biết khách đã đi chuyến.
 *
 * <p>Tắt bằng {@code app.booking.completion-job.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.booking.completion-job.enabled", havingValue = "true", matchIfMissing = true)
public class TripCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(TripCompletionJob.class);

    private final TripLifecycleService tripLifecycleService;

    public TripCompletionJob(TripLifecycleService tripLifecycleService) {
        this.tripLifecycleService = tripLifecycleService;
    }

    @Scheduled(
            fixedDelayString = "${app.booking.completion-job.interval-ms:300000}",
            initialDelayString = "${app.booking.completion-job.initial-delay-ms:60000}")
    public void run() {
        TripLifecycleService.CompletionSweep sweep = tripLifecycleService.completeArrivedTrips(LocalDateTime.now());
        if (sweep.trips() > 0 || sweep.bookings() > 0) {
            log.info("Job hoàn thành chuyến: {} chuyến, {} đơn chuyển sang COMPLETED", sweep.trips(), sweep.bookings());
        }
    }
}
