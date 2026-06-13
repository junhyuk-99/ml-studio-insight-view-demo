package com.demo.insight.thresholdalert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ThresholdAlertAckRequestDto(
        @JsonProperty("alertId")
        @NotBlank(message = "alertId is required.")
        String alertId,

        @JsonProperty("ackBy")
        @NotBlank(message = "ackBy is required.")
        String ackBy,

        @JsonProperty("memo")
        String memo
) {
}
