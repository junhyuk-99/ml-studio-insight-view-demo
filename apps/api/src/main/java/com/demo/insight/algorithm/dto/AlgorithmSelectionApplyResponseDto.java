package com.demo.insight.algorithm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AlgorithmSelectionApplyResponseDto(
        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("active_policy_id")
        String activePolicyId,

        @JsonProperty("active_algo_code")
        String activeAlgoCode,

        @JsonProperty("active_algo_name")
        String activeAlgoName,

        @JsonProperty("changed_by")
        String changedBy,

        @JsonProperty("changed_reason")
        String changedReason,

        @JsonProperty("use_flag")
        String useFlag,

        @JsonProperty("reg_date")
        String regDate,

        @JsonProperty("updated_at")
        String updatedAt
) {
}
