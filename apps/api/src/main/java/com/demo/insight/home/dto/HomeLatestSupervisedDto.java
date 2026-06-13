package com.demo.insight.home.dto;

public record HomeLatestSupervisedDto(
        boolean available,
        String runId,
        String runAt,
        String status,
        long resultCount,
        long anomalyCount,
        Double accuracy,
        Double precision,
        Double recall,
        Double f1Score,
        String message
) {
}
