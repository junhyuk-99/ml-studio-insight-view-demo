package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record AiOverviewAnomalySummaryDto(
        @JsonProperty("avg_anomaly_score")
        Double avgAnomalyScore,

        @JsonProperty("avg_health_index")
        Double avgHealthIndex,

        @JsonProperty("anomaly_count")
        long anomalyCount,

        @JsonProperty("total_count")
        long totalCount,

        @JsonProperty("status_counts")
        Map<String, Long> statusCounts
) {
}
