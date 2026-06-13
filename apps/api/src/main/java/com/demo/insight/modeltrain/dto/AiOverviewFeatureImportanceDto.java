package com.demo.insight.modeltrain.dto;

public record AiOverviewFeatureImportanceDto(
        int rank,

        String feature,

        Double importance
) {
}
