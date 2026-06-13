package com.demo.insight.thresholdalert.dto;

public record ThresholdAlertTrendPointDto(
        String alertId,
        String datasetKey,
        String runId,
        String windowStart,
        String windowEnd,
        String createdAt,
        String displayName,
        String targetField,
        Double value,
        Double valuePercent,
        String severity,
        Double warningValue,
        Double warningValuePercent,
        Double criticalValue,
        Double criticalValuePercent,
        String status,
        String ackYn
) {
}
