package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record AnomalyRunDetailDto(
        @JsonProperty("run_id")
        String runId,

        @JsonProperty("policy_id")
        String policyId,

        @JsonProperty("algo_code")
        String algoCode,

        @JsonProperty("algo_name")
        String algoName,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("dataset_name")
        String datasetName,

        @JsonProperty("equipment_id")
        String equipmentId,

        @JsonProperty("sensor_id")
        String sensorId,

        @JsonProperty("trigger_type")
        String triggerType,

        String status,

        @JsonProperty("selected_columns")
        List<String> selectedColumns,

        @JsonProperty("window_size")
        Integer windowSize,

        Map<String, Object> params,

        @JsonProperty("reg_date")
        String regDate,

        @JsonProperty("updated_at")
        String updatedAt
) {
}
