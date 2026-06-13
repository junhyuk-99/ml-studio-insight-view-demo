package com.demo.insight.preprocess.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FeatureAutoTriggerResponseDto(
        @JsonProperty("requested_job_count")
        int requestedJobCount,

        @JsonProperty("executed_job_count")
        int executedJobCount,

        @JsonProperty("success_job_count")
        int successJobCount,

        @JsonProperty("results")
        List<FeatureAutoTriggerResultDto> results
) {
}
