package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiOverviewLatestRunDto(
        @JsonProperty("run_id")
        String runId,

        String status,

        @JsonProperty("trigger_type")
        String triggerType,

        @JsonProperty("started_at")
        String startedAt,

        @JsonProperty("ended_at")
        String endedAt,

        @JsonProperty("executed_at")
        String executedAt,

        @JsonProperty("algo_code")
        String algoCode,

        @JsonProperty("algo_name")
        String algoName,

        @JsonProperty("dataset_key")
        String datasetKey,

        String message
) {
}
