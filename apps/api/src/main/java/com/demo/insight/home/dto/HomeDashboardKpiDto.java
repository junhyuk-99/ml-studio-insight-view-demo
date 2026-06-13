package com.demo.insight.home.dto;

public record HomeDashboardKpiDto(
        long activeModelCount,
        String latestRunStatus,
        String latestRunAt,
        String latestResultStatus,
        long anomalyCount,
        long totalResultCount,
        long datasetCount,
        long fieldCount,
        String latestUpdatedAt
) {
}
