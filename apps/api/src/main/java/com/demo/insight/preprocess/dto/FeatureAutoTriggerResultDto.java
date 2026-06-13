package com.demo.insight.preprocess.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FeatureAutoTriggerResultDto(
        @JsonProperty("dataset_label")
        String datasetLabel,

        @JsonProperty("status")
        String status,

        @JsonProperty("message")
        String message,

        @JsonProperty("total_window_count")
        int totalWindowCount,

        @JsonProperty("created_count")
        int createdCount,

        @JsonProperty("skipped_count")
        int skippedCount,

        @JsonProperty("consumed_raw_count")
        int consumedRawCount
) {
}
