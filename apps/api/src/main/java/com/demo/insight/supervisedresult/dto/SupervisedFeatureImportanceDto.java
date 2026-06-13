package com.demo.insight.supervisedresult.dto;

public record SupervisedFeatureImportanceDto(
        int rank,
        String feature,
        Double importance
) {
}

