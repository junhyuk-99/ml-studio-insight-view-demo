package com.demo.insight.home.dto;

public record HomeActiveModelDto(
        String datasetKey,
        String algoCode,
        String algoName,
        String policyId,
        String modelType,
        String status
) {
}
