package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExecuteModelRunResponseDto(
        @JsonProperty("run_id")
        String runId,

        String status,

        @JsonProperty("processed_window_count")
        int processedWindowCount,

        @JsonProperty("saved_result_count")
        int savedResultCount,

        @JsonProperty("meta_only_param_keys")
        List<String> metaOnlyParamKeys
) {
}
