package com.demo.insight.healthindex.dto;

public record HealthIndexPointDto(
        String runId,
        String datasetKey,
        String equipmentId,
        String windowStart,
        String windowEnd,
        Double healthIndex,
        Double healthIndexPercent,
        Double anomalyScore,
        String status,
        Boolean isAnomaly,
        String regDate
) {
}
