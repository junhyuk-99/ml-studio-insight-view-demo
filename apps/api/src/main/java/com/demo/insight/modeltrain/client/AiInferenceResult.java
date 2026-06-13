package com.demo.insight.modeltrain.client;

public record AiInferenceResult(
        Double anomalyScore,
        Boolean isAnomaly,
        Double healthIndex,
        String status
) {
}
