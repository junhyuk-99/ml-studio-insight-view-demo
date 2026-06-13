package com.demo.insight.thresholdalert.dto;

public record ThresholdAlertSummaryDto(
        long totalCount,
        long openCount,
        long ackCount,
        long warningCount,
        long criticalCount,
        String latestAlertAt,
        String latestSeverity,
        String latestDisplayName
) {
}
