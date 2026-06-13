package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiOverviewLabeledDataSummaryDto(
        @JsonProperty("source_collection")
        String sourceCollection,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("label_version")
        String labelVersion,

        @JsonProperty("total_count")
        long totalCount,

        @JsonProperty("train_count")
        long trainCount,

        @JsonProperty("test_count")
        long testCount,

        @JsonProperty("excluded_unknown_count")
        long excludedUnknownCount,

        @JsonProperty("normal_count")
        long normalCount,

        @JsonProperty("anomaly_count")
        long anomalyCount
) {
}
