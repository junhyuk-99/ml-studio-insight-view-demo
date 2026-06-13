package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ModelTrainAutoPolicyListResponseDto(
        @JsonProperty("scheduler_enabled")
        boolean schedulerEnabled,

        @JsonProperty("scheduler_fixed_delay_ms")
        long schedulerFixedDelayMs,

        @JsonProperty("policies")
        List<ModelTrainAutoPolicyStatusDto> policies
) {
}

