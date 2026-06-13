package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record TimeseriesDataResponseDto(
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

        @JsonProperty("max_points")
        int maxPoints,

        @JsonProperty("total_row_count")
        long totalRowCount,

        @JsonProperty("sampled_row_count")
        long sampledRowCount,

        @JsonProperty("sampling_applied")
        boolean samplingApplied,

        @JsonProperty("sampling_step")
        long samplingStep,

        @JsonProperty("field_timeseries")
        List<TimeseriesFieldDataDto> fieldTimeseries
) {
}
