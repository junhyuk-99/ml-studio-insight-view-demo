package com.demo.insight.home.dto;

public record HomeRecentRunDto(
        String runId,
        String runAt,
        String algoCode,
        String algoName,
        String datasetKey,
        String analysisType,
        String status,
        long resultCount,
        Long anomalyCount,
        Double accuracy,
        Double f1Score
) {
}
