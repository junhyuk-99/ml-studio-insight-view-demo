package com.demo.insight.anomalycause.dto;

public record AnomalyCauseRecalculateRunResultDto(
        String runId,
        String datasetKey,
        String equipmentId,
        int processedCount,
        int createdOrUpdatedCount,
        int skippedCount
) {
}
