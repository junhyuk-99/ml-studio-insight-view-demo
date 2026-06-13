package com.demo.insight.preprocess.repository;

import java.util.List;
import java.util.Map;

public record RawPreviewResult(
        List<String> availableColumns,
        List<String> metadataColumns,
        List<String> numericColumns,
        Map<String, String> columnLabels,
        List<String> datasetKeyColumns,
        List<Map<String, String>> datasetKeys,
        List<Map<String, Object>> rawRows
) {
}
