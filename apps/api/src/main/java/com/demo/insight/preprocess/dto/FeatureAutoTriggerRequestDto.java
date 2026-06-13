package com.demo.insight.preprocess.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FeatureAutoTriggerRequestDto(
        @JsonProperty("equipment_id")
        String equipmentId,

        @JsonProperty("sensor_id")
        String sensorId,

        @JsonProperty("dataset_key")
        String datasetKey
) {
}
