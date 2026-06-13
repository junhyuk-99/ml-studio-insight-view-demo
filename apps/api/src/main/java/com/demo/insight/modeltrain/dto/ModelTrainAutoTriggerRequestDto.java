package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelTrainAutoTriggerRequestDto(
        @JsonProperty("policy_id")
        String policyId,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("algo_code")
        String algoCode,

        @JsonProperty("requested_by_role")
        String requestedByRole,

        @JsonProperty("force_run")
        Boolean forceRun
) {
}
