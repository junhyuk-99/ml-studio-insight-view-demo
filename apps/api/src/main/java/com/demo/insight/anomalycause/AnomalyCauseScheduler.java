package com.demo.insight.anomalycause;

import com.demo.insight.anomalycause.dto.AnomalyCauseBackfillResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnomalyCauseScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnomalyCauseScheduler.class);

    private final AnomalyCauseService anomalyCauseService;

    @Value("${app.anomaly-cause.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${app.anomaly-cause.scheduler.batch-size:200}")
    private int batchSize;

    public AnomalyCauseScheduler(AnomalyCauseService anomalyCauseService) {
        this.anomalyCauseService = anomalyCauseService;
    }

    @Scheduled(
            fixedDelayString = "${app.anomaly-cause.scheduler.fixed-delay-ms:600000}",
            initialDelayString = "${app.anomaly-cause.scheduler.initial-delay-ms:120000}"
    )
    public void backfillMissingAnomalyCause() {
        if (!schedulerEnabled) {
            return;
        }

        int resolvedBatchSize = Math.max(1, batchSize);
        log.info("Anomaly cause scheduler started. batchSize={}", resolvedBatchSize);

        try {
            AnomalyCauseBackfillResultDto result = anomalyCauseService.backfillMissingCauses(resolvedBatchSize);
            if (result.targetCount() == 0) {
                log.debug("Anomaly cause scheduler finished with no missing targets.");
                return;
            }

            log.info(
                    "Anomaly cause scheduler finished. targetCount={}, processedCount={}, successCount={}, failureCount={}, skippedCount={}",
                    result.targetCount(),
                    result.processedCount(),
                    result.successCount(),
                    result.failureCount(),
                    result.skippedCount()
            );
        } catch (Exception exception) {
            log.error("Anomaly cause scheduler failed.", exception);
        }
    }
}
