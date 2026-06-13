package com.demo.insight.anomalycause.dto;

public record AnomalyCauseRunDto(
        String runId,
        String datasetKey,
        String algoCode,
        String algoName,
        String label,
        String executedAt
) {
}
