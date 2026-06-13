package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiOverviewSupervisedSummaryDto(
        Double accuracy,

        Double precision,

        Double recall,

        @JsonProperty("f1_score")
        Double f1Score,

        @JsonProperty("test_count")
        long testCount,

        @JsonProperty("train_count")
        long trainCount,

        @JsonProperty("total_count")
        long totalCount,

        @JsonProperty("excluded_unknown_count")
        long excludedUnknownCount,

        @JsonProperty("normal_count")
        long normalCount,

        @JsonProperty("anomaly_count")
        long anomalyCount,

        long tp,

        long tn,

        long fp,

        long fn,

        @JsonProperty("correct_count")
        long correctCount,

        @JsonProperty("misclassified_count")
        long misclassifiedCount,

        @JsonProperty("classification_result_count")
        long classificationResultCount,

        @JsonProperty("latest_eval_executed_at")
        String latestEvalExecutedAt,

        @JsonProperty("feature_importance_top")
        List<AiOverviewFeatureImportanceDto> featureImportanceTop
) {
}
