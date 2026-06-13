package com.demo.insight.modeltrain.dto;

import java.util.List;
import java.util.Map;

public record AnomalyResultListResponseDto(
        List<Map<String, Object>> anomalyResults
) {
}
