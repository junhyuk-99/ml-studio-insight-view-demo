package com.demo.insight.anomalycause.dto;

public record AnomalyCauseBackfillResultDto(
        int requestedLimit,
        int targetCount,
        int processedCount,
        int successCount,
        int failureCount,
        int skippedCount
) {
}
