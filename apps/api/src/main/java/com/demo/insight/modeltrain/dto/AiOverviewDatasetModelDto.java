package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiOverviewDatasetModelDto(
        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("dataset_label")
        String datasetLabel,

        @JsonProperty("source_collection")
        String sourceCollection,

        @JsonProperty("active_policy_id")
        String activePolicyId,

        @JsonProperty("active_algo_code")
        String activeAlgoCode,

        @JsonProperty("active_algo_name")
        String activeAlgoName,

        @JsonProperty("summary_type")
        String summaryType,

        @JsonProperty("window_size")
        Integer windowSize,

        @JsonProperty("selected_column_count")
        int selectedColumnCount,

        @JsonProperty("reg_date")
        String regDate,

        @JsonProperty("updated_at")
        String updatedAt,

        @JsonProperty("latest_run")
        AiOverviewLatestRunDto latestRun,

        @JsonProperty("summary")
        AiOverviewAnomalySummaryDto summary,

        @JsonProperty("supervised_summary")
        AiOverviewSupervisedSummaryDto supervisedSummary,

        @JsonProperty("feature_summary")
        AiOverviewFeatureSummaryDto featureSummary,

        @JsonProperty("labeled_data_summary")
        AiOverviewLabeledDataSummaryDto labeledDataSummary
) {
}
