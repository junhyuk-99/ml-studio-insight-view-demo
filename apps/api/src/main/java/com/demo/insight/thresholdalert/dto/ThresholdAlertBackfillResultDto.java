package com.demo.insight.thresholdalert.dto;

public record ThresholdAlertBackfillResultDto(
        int requestLimit,
        int targetCount,
        int processedCount,
        int successCount,
        int failureCount,
        int skippedCount
) {
}
