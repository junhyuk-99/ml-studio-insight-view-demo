package com.demo.insight.anomalycause.dto;

public record CauseCandidateDto(
        Integer rank,
        String feature,
        String sourceField,
        String stat,
        String displayName,
        String causeGroup,
        String unit,
        Double currentValue,
        Double baselineMean,
        Double baselineStd,
        Double deviationScore,
        String direction,
        String reasonText
) {
}
