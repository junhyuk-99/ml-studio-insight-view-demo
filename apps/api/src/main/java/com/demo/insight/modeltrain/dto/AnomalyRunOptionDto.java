package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnomalyRunOptionDto(
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

        @JsonProperty("trigger_type")
        String triggerType,

        @JsonProperty("window_size")
        Integer windowSize,

        String status,

        @JsonProperty("reg_date")
        String regDate,

        @JsonProperty("updated_at")
        String updatedAt
) {
}
