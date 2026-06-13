package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiOverviewActiveModelDto(
        @JsonProperty("algo_code")
        String algoCode,

        @JsonProperty("algo_name")
        String algoName,

        @JsonProperty("active_policy_id")
        String activePolicyId,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("dataset_label")
        String datasetLabel,

        @JsonProperty("window_size")
        Integer windowSize,

        @JsonProperty("selected_column_count")
        int selectedColumnCount,

        @JsonProperty("updated_at")
        String updatedAt
) {
}
