package com.demo.insight.preprocess.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FeatureAutoJobListResponseDto(
        @JsonProperty("scheduler_enabled")
        boolean schedulerEnabled,

        @JsonProperty("scheduler_fixed_delay_ms")
        long schedulerFixedDelayMs,

        @JsonProperty("jobs")
        List<FeatureAutoJobStatusDto> jobs
) {
}
