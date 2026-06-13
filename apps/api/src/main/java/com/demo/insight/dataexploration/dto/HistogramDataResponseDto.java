package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record HistogramDataResponseDto(
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
        int bins,

        @JsonProperty("total_row_count")
        long totalRowCount,

        @JsonProperty("field_histograms")
        List<HistogramFieldDataDto> fieldHistograms
) {
}
