package com.demo.insight.supervisedresult.dto;

import java.util.List;

public record SupervisedPredictionRowDto(
        String timestamp,
        Integer actualLabel,
        Integer predictionLabel,
        Double probabilityAnomaly,
        Double probabilityNormal,
        Double probability,
        String correctYn,
        String errorType,
        List<String> topFeatures
) {
}
