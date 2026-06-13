package com.demo.insight.preprocess.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record FeatureAutoJobUpsertRequestDto(
        @JsonProperty("equipment_id")
        String equipmentId,

        @JsonProperty("sensor_id")
        String sensorId,

        @JsonProperty("dataset_name")
        String datasetName,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("selected_columns")
        @NotEmpty(message = "selected_columns must not be empty.")
        List<String> selectedColumns,

        @JsonProperty("window_size")
        @Min(value = 1, message = "window_size must be greater than or equal to 1.")
        Integer windowSize,

        @JsonProperty("target_collection")
        String targetCollection,

        @JsonProperty("schedule_interval_seconds")
        @Min(value = 1, message = "schedule_interval_seconds must be greater than or equal to 1.")
        Integer scheduleIntervalSeconds,

        @JsonProperty("use_yn")
        Boolean useYn
) {
}
