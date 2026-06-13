package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FeatureDatasetDto(
        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("dataset_label")
        String datasetLabel,

        @JsonProperty("dataset_name")
        String datasetName,

        @JsonProperty("equipment_id")
        String equipmentId,

        @JsonProperty("source_collection")
        String sourceCollection,

        @JsonProperty("target_collection")
        String targetCollection,

        @JsonProperty("selected_columns")
        List<String> selectedColumns,

        @JsonProperty("window_size")
        Integer windowSize,

        @JsonProperty("window_mode")
        String windowMode,

        @JsonProperty("feature_stats")
        List<String> featureStats,

        @JsonProperty("scheduler_enabled")
        Boolean schedulerEnabled,

        @JsonProperty("scheduler_interval_sec")
        Integer schedulerIntervalSec,

        @JsonProperty("last_status")
        String lastStatus,

        @JsonProperty("last_window_end")
        String lastWindowEnd,

        @JsonProperty("last_checkpoint_value")
        String lastCheckpointValue,

        @JsonProperty("config_source")
        String configSource,

        @JsonProperty("config_message")
        String configMessage,

        @JsonProperty("SOURCE_TYPE_CODE")
        String sourceTypeCode,

        @JsonProperty("SOURCE_DTL_CODE")
        String sourceDtlCode,

        @JsonProperty("SOURCE_FILE")
        String sourceFile
) {
}
