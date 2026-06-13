package com.demo.insight.preprocess.dto;

import java.util.List;
import java.util.Map;

public record RawDataPreviewResponseDto(
        String sourceCollection,
        String datasetKey,
        String datasetName,
        String datasetDisplayName,
        List<String> availableColumns,
        List<String> metadataColumns,
        List<String> numericColumns,
        Map<String, String> columnLabels,
        List<String> datasetKeyColumns,
        List<Map<String, String>> datasetKeys,
        List<Map<String, Object>> rawRows
) {
}
