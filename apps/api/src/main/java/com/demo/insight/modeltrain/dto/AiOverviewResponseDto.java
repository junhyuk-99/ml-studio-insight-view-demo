package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiOverviewResponseDto(
        @JsonProperty("active_models")
        List<AiOverviewDatasetModelDto> activeModels,

        @JsonProperty("total_active_model_count")
        int totalActiveModelCount,

        @JsonProperty("active_model")
        AiOverviewActiveModelDto activeModel,

        @JsonProperty("latest_run")
        AiOverviewLatestRunDto latestRun,

        @JsonProperty("anomaly_summary")
        AiOverviewAnomalySummaryDto anomalySummary,

        @JsonProperty("supervised_summary")
        AiOverviewSupervisedSummaryDto supervisedSummary,

        @JsonProperty("feature_summary")
        AiOverviewFeatureSummaryDto featureSummary,

        @JsonProperty("labeled_data_summary")
        AiOverviewLabeledDataSummaryDto labeledDataSummary,

        @JsonProperty("refreshed_at")
        String refreshedAt
) {
}
