package com.demo.insight.modeltrain.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRandomForestFeatureImportanceResult(
        Integer rank,
        String feature,
        @JsonProperty("importance")
        Double importance
) {
}

