package com.demo.insight.modeltrain.dto;

import java.util.List;

public record HomeInsightRecentWindowDto(
        String id,
        String windowStart,
        String windowEnd,
        String status,
        Double anomalyScore,
        List<String> causes
) {
}
