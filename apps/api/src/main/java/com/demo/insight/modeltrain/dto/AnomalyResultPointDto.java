package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record AnomalyResultPointDto(
        @JsonProperty("run_id")
        String runId,

        @JsonProperty("algo_code")
        String algoCode,

        @JsonProperty("algo_name")
        String algoName,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("equipment_id")
        String equipmentId,

        String status,

        @JsonProperty("anomaly_score")
        Double anomalyScore,

        @JsonProperty("health_index")
        Double healthIndex,

        @JsonProperty("is_anomaly")
        Boolean isAnomaly,

        @JsonProperty("window_start")
        String windowStart,

        @JsonProperty("window_end")
        String windowEnd,

        @JsonProperty("reg_date")
        String regDate,

        @JsonProperty("input_features")
        Map<String, Object> inputFeatures
) {
}
