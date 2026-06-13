package com.demo.insight.anomalycause.dto;

import java.util.List;

public record AnomalyCauseListItemDto(
        String id,
        String runId,
        String datasetKey,
        String equipmentId,
        String windowStart,
        String windowEnd,
        Double anomalyScore,
        Double healthIndex,
        String status,
        boolean causeGenerated,
        List<String> causeSummary,
        String topCauseGroup,
        Double topDeviationScore
) {
}
