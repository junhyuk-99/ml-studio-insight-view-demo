package com.demo.insight.modeltrain.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRandomForestExecutionResult(
        @JsonProperty("run_id")
        String runId,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("algo_code")
        String algoCode,

        Double accuracy,
        Double precision,
        Double recall,

        @JsonProperty("f1_score")
        Double f1Score,

        Integer tp,
        Integer tn,
        Integer fp,
        Integer fn,

        @JsonProperty("train_count")
        Integer trainCount,

        @JsonProperty("test_count")
        Integer testCount,

        @JsonProperty("total_count")
        Integer totalCount,

        @JsonProperty("excluded_unknown_count")
        Integer excludedUnknownCount,

        List<AiRandomForestPredictionResult> predictions,

        @JsonProperty("feature_importances")
        List<AiRandomForestFeatureImportanceResult> featureImportances
) {
}
