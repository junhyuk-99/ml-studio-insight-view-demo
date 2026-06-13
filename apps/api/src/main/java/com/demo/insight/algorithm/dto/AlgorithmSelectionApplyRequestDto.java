package com.demo.insight.algorithm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AlgorithmSelectionApplyRequestDto(
        @JsonProperty("dataset_key")
        @NotBlank(message = "dataset_key is required.")
        String datasetKey,

        @JsonProperty("algo_code")
        @NotBlank(message = "algo_code is required.")
        String algoCode,

        @JsonProperty("changed_by")
        String changedBy,

        @JsonProperty("changed_reason")
        String changedReason
) {
}
