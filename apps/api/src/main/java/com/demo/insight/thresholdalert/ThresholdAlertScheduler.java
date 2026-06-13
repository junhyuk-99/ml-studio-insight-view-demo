package com.demo.insight.thresholdalert;

import com.demo.insight.thresholdalert.dto.ThresholdAlertBackfillResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ThresholdAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(ThresholdAlertScheduler.class);

    private final ThresholdAlertService thresholdAlertService;

    @Value("${app.threshold-alert.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${app.threshold-alert.scheduler.batch-size:200}")
    private int batchSize;

    public ThresholdAlertScheduler(ThresholdAlertService thresholdAlertService) {
        this.thresholdAlertService = thresholdAlertService;
    }

    @Scheduled(
            fixedDelayString = "${app.threshold-alert.scheduler.fixed-delay-ms:600000}",
            initialDelayString = "${app.threshold-alert.scheduler.initial-delay-ms:180000}"
    )
    public void backfillMissingThresholdAlerts() {
        if (!schedulerEnabled) {
            return;
        }

        int resolvedBatchSize = Math.max(1, batchSize);
        log.info("Threshold alert scheduler started. batchSize={}", resolvedBatchSize);

        try {
            ThresholdAlertBackfillResultDto result = thresholdAlertService.backfillMissingAlerts(resolvedBatchSize);
            if (result.targetCount() == 0) {
                log.debug("Threshold alert scheduler finished with no missing targets.");
                return;
            }

            log.info(
                    "Threshold alert scheduler finished. targetCount={}, processedCount={}, successCount={}, failureCount={}, skippedCount={}",
                    result.targetCount(),
                    result.processedCount(),
                    result.successCount(),
                    result.failureCount(),
                    result.skippedCount()
            );
        } catch (Exception exception) {
            log.error("Threshold alert scheduler failed.", exception);
        }
    }
}
