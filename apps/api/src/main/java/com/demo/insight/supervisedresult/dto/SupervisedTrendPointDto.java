package com.demo.insight.supervisedresult.dto;

public record SupervisedTrendPointDto(
        String runId,
        String regDate,
        String triggerType,
        Double accuracy,
        Double precision,
        Double recall,
        Double f1Score
) {
}

