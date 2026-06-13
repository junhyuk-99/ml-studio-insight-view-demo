package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelTrainAutoTriggerResultDto(
        @JsonProperty("policy_id")
        String policyId,

        @JsonProperty("dataset_label")
        String datasetLabel,

        String status,
        String message,

        @JsonProperty("run_id")
        String runId,

        @JsonProperty("processed_window_count")
        int processedWindowCount,

        @JsonProperty("saved_result_count")
        int savedResultCount,

        @JsonProperty("new_feature_count")
        long newFeatureCount,

        @JsonProperty("total_feature_count")
        long totalFeatureCount
) {
}

