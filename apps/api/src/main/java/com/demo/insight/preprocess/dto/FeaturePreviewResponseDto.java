package com.demo.insight.preprocess.dto;

import java.util.List;
import java.util.Map;

public record FeaturePreviewResponseDto(
        String datasetKey,
        List<String> availableColumns,
        List<Map<String, Object>> featureRows
) {
}
