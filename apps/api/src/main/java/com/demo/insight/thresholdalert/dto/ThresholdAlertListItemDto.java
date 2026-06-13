package com.demo.insight.thresholdalert.dto;

public record ThresholdAlertListItemDto(
        String alertId,
        String ruleId,
        String datasetKey,
        String runId,
        String windowStart,
        String windowEnd,
        String targetType,
        String targetField,
        String displayName,
        Double value,
        String severity,
        String operator,
        Double warningValue,
        Double criticalValue,
        String status,
        String ackYn,
        String ackBy,
        String ackAt,
        String memo,
        String createdAt,
        String updatedAt
) {
}
