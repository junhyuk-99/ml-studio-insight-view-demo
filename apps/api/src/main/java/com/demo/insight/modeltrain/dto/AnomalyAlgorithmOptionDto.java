package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnomalyAlgorithmOptionDto(
        @JsonProperty("algo_code")
        String algoCode,

        @JsonProperty("algo_name")
        String algoName
) {
}
