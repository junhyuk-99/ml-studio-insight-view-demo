package com.demo.insight.preprocess.dto;

public record DataSourceDatasetDto(
        String datasetKey,
        String datasetName,
        String displayName,
        String sourceCollection,
        String targetFeatureCollection,
        String datasetPurpose,
        Boolean featureEnabled,
        String labelField,
        Integer sortNo,
        String equipmentGroup,
        String equipmentGroupName
) {
}