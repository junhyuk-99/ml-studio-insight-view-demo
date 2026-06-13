package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ExecuteModelRunRequestDto(
        @JsonProperty("run_id")
        @NotBlank(message = "run_id is required.")
        String runId
) {
}
