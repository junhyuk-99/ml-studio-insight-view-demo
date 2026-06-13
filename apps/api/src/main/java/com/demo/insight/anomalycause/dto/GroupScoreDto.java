package com.demo.insight.anomalycause.dto;

public record GroupScoreDto(
        String causeGroup,
        Double score,
        String topFeature,
        Integer rank
) {
}
