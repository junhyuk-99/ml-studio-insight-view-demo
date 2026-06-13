package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnomalyRunListResponseDto(
        List<AnomalyAlgorithmOptionDto> algorithms,
        List<AnomalyRunOptionDto> runs,

        @JsonProperty("latest_run_id")
        String latestRunId,

        @JsonProperty("latest_success_run_id")
        String latestSuccessRunId
) {
}
