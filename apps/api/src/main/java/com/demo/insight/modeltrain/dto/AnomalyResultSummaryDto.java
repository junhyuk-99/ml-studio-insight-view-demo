package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnomalyResultSummaryDto(
        String status,

        @JsonProperty("latest_anomaly_score")
        Double latestAnomalyScore,

        @JsonProperty("avg_anomaly_score")
        Double avgAnomalyScore,

        @JsonProperty("latest_health_index")
        Double latestHealthIndex,

        @JsonProperty("avg_health_index")
        Double avgHealthIndex,

        @JsonProperty("integrated_health")
        Double integratedHealth,

        @JsonProperty("integrated_status")
        String integratedStatus,

        @JsonProperty("if_normalized_health")
        Double ifNormalizedHealth,

        @JsonProperty("ae_normalized_health")
        Double aeNormalizedHealth,

        @JsonProperty("if_score_raw")
        Double ifScoreRaw,

        @JsonProperty("ae_score_raw")
        Double aeScoreRaw,

        @JsonProperty("anomaly_count")
        int anomalyCount,

        @JsonProperty("total_count")
        int totalCount
) {
}
