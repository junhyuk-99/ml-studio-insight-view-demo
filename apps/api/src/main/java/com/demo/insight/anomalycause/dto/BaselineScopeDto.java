package com.demo.insight.anomalycause.dto;

public record BaselineScopeDto(
        String datasetKey,
        String statusFilter,
        String from,
        String to,
        Long sampleCount
) {
}
