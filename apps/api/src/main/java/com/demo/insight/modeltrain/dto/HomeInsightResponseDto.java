package com.demo.insight.modeltrain.dto;

import java.util.List;

public record HomeInsightResponseDto(
        String runId,
        List<HomeInsightTrendPointDto> trend,
        List<HomeInsightTopSensorDto> topSensors,
        List<HomeInsightRecentWindowDto> recentWindows
) {
}
