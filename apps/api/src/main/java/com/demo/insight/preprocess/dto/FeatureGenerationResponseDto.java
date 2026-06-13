package com.demo.insight.preprocess.dto;

public record FeatureGenerationResponseDto(
        int totalWindowCount,
        int createdCount,
        int skippedCount
) {
}
