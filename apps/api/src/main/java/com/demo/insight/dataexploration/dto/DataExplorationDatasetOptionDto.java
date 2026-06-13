package com.demo.insight.dataexploration.dto;

public record DataExplorationDatasetOptionDto(
        String datasetKey,
        String datasetName,
        String displayName,
        String typeCode,
        String dtlCode,
        String sourceCollection,
        String datasetPurpose,
        Boolean featureEnabled,
        Integer sortNo
) {
}
