package com.demo.insight.thresholdalert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ThresholdAlertRecalculateRunRequestDto(
        @JsonProperty("runId")
        @NotBlank(message = "runId is required.")
        String runId,

        @JsonProperty("datasetKey")
        @NotBlank(message = "datasetKey is required.")
        String datasetKey
) {
}
