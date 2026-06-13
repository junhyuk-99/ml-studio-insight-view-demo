package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FeatureDatasetListResponseDto(
        @JsonProperty("feature_datasets")
        List<FeatureDatasetDto> featureDatasets
) {
}
