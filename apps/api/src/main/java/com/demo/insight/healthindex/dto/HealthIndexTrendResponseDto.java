package com.demo.insight.healthindex.dto;

import java.util.List;

public record HealthIndexTrendResponseDto(
        String runId,
        String datasetKey,
        HealthIndexSummaryDto summary,
        List<HealthIndexPointDto> points
) {
}
