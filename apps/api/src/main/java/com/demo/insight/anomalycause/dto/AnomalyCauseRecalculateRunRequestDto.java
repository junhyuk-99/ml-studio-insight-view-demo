package com.demo.insight.anomalycause.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AnomalyCauseRecalculateRunRequestDto(
        @JsonProperty("runId")
        @NotBlank(message = "runId is required.")
        String runId,

        @JsonProperty("datasetKey")
        @NotBlank(message = "datasetKey is required.")
        String datasetKey,

        @JsonProperty("equipmentId")
        @NotBlank(message = "equipmentId is required.")
        String equipmentId
) {
}
