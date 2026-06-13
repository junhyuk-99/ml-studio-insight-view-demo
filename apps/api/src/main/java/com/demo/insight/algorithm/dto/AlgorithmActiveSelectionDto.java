package com.demo.insight.algorithm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AlgorithmActiveSelectionDto(
        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("active_policy_id")
        String activePolicyId,

        @JsonProperty("active_algo_code")
        String activeAlgoCode,

        @JsonProperty("active_algo_name")
        String activeAlgoName,

        @JsonProperty("updated_at")
        String updatedAt
) {
}
