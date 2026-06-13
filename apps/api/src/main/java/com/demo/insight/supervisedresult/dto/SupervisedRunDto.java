package com.demo.insight.supervisedresult.dto;

public record SupervisedRunDto(
        String runId,
        String datasetKey,
        String algoCode,
        String algoName,
        String status,
        String triggerType,
        String executedAt,
        long totalPredictions
) {
}
