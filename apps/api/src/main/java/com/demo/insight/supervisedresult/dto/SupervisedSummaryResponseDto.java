package com.demo.insight.supervisedresult.dto;

import java.util.List;

public record SupervisedSummaryResponseDto(
        String runId,
        String datasetKey,
        String algoCode,
        String algoName,
        String status,
        String triggerType,
        String executedAt,
        long totalPredictions,
        SupervisedMetricDto accuracy,
        SupervisedMetricDto precision,
        SupervisedMetricDto recall,
        SupervisedMetricDto f1Score,
        long tp,
        long tn,
        long fp,
        long fn,
        List<SupervisedFeatureImportanceDto> featureImportances
) {
}
