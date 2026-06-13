package com.demo.insight.healthindex.dto;

public record HealthIndexRunDto(
        String runId,
        String datasetKey,
        String algoCode,
        String algoName,
        String label,
        String executedAt
) {
}
