package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ModelRunDto(
        @JsonProperty("run_id")
        String runId,

        @JsonProperty("equipment_id")
        String equipmentId,

        @JsonProperty("sensor_id")
        String sensorId,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("selected_columns")
        List<String> selectedColumns,

        @JsonProperty("window_size")
        Integer windowSize,

        @JsonProperty("algo_code")
        String algoCode,

        @JsonProperty("algo_name")
        String algoName,

        Map<String, Object> params,
        String status,

        @JsonProperty("reg_date")
        String regDate
) {
}
