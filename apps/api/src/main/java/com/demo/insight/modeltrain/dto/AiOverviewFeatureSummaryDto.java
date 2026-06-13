package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiOverviewFeatureSummaryDto(
        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("dataset_label")
        String datasetLabel,

        @JsonProperty("source_dataset_name")
        String sourceDatasetName,

        @JsonProperty("total_feature_count")
        long totalFeatureCount,

        @JsonProperty("latest_feature_created_at")
        String latestFeatureCreatedAt,

        @JsonProperty("window_size")
        Integer windowSize,

        @JsonProperty("selected_column_count")
        int selectedColumnCount
) {
}
