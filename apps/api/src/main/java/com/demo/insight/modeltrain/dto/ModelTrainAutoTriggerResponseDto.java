package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ModelTrainAutoTriggerResponseDto(
        @JsonProperty("requested_policy_count")
        int requestedPolicyCount,

        @JsonProperty("executed_policy_count")
        int executedPolicyCount,

        @JsonProperty("success_policy_count")
        int successPolicyCount,

        @JsonProperty("results")
        List<ModelTrainAutoTriggerResultDto> results
) {
}

