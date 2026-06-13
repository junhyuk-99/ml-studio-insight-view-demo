package com.demo.insight.thresholdalert.dto;

public record ThresholdAlertRecalculateRunResultDto(
        String runId,
        String datasetKey,
        int processedCount,
        int createdOrUpdatedCount,
        int skippedCount,
        int warningCount,
        int criticalCount
) {
}
