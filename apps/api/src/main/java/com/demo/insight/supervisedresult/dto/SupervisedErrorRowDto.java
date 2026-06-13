package com.demo.insight.supervisedresult.dto;

import java.util.List;

public record SupervisedErrorRowDto(
        String timestamp,
        Integer actualLabel,
        Integer predictionLabel,
        Double probabilityAnomaly,
        Double probabilityNormal,
        Double probability,
        List<String> topFeatures
) {
}
