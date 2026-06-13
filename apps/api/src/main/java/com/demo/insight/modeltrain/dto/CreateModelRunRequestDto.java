package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record CreateModelRunRequestDto(
        @JsonProperty("equipment_id")
        String equipmentId,

        @JsonProperty("sensor_id")
        String sensorId,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("selected_columns")
        @NotEmpty(message = "selected_columns must not be empty.")
        List<@NotBlank(message = "selected_columns must not contain blank values.") String> selectedColumns,

        @JsonProperty("window_size")
        @NotNull(message = "window_size is required.")
        @Min(value = 1, message = "window_size must be greater than or equal to 1.")
        Integer windowSize,

        @JsonProperty("algo_code")
        @NotBlank(message = "algo_code is required.")
        String algoCode,

        @JsonProperty("algo_name")
        @NotBlank(message = "algo_name is required.")
        String algoName,

        Map<String, Object> params
) {
}
