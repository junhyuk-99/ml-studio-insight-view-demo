package com.demo.insight.modeltrain.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRandomForestPredictionResult(
        @JsonProperty("source_index")
        Integer sourceIndex,

        @JsonProperty("labeled_doc_id")
        String labeledDocId,

        @JsonProperty("source_id")
        String sourceId,

        @JsonProperty("actual_label")
        Integer actualLabel,

        @JsonProperty("prediction_label")
        Integer predictionLabel,

        @JsonProperty("prediction_probability")
        Double predictionProbability,

        @JsonProperty("prediction_probability_normal")
        Double predictionProbabilityNormal,

        @JsonProperty("prediction_probability_anomaly")
        Double predictionProbabilityAnomaly,

        @JsonProperty("split_type")
        String splitType,

        @JsonProperty("error_type")
        String errorType
) {
}
