package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record CorrelationHeatmapDataResponseDto(
        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("dataset_name")
        String datasetName,

        @JsonProperty("display_name")
        String displayName,

        @JsonProperty("source_collection")
        String sourceCollection,

        @JsonProperty("applied_match_filter")
        Map<String, Object> appliedMatchFilter,

        @JsonProperty("current_range")
        DataExplorationCurrentRangeDto currentRange,

        String from,
        String to,
        String method,

        @JsonProperty("max_rows")
        int maxRows,

        @JsonProperty("total_row_count")
        long totalRowCount,

        @JsonProperty("effective_row_count")
        long effectiveRowCount,

        @JsonProperty("sampled_row_count")
        long sampledRowCount,

        @JsonProperty("sampling_applied")
        boolean samplingApplied,

        @JsonProperty("sampling_step")
        long samplingStep,

        List<String> fields,
        List<List<Double>> matrix,

        @JsonProperty("pair_sample_counts")
        List<List<Long>> pairSampleCounts
) {
}
