package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record HistogramFieldListResponseDto(
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

        @JsonProperty("min_timestamp")
        String minTimestamp,

        @JsonProperty("max_timestamp")
        String maxTimestamp,

        @JsonProperty("default_from")
        String defaultFrom,

        @JsonProperty("default_to")
        String defaultTo,

        @JsonProperty("row_count")
        long rowCount,

        List<HistogramFieldOptionDto> fields,

        @JsonProperty("default_selected_fields")
        List<String> defaultSelectedFields,

        @JsonProperty("group_by_fields")
        List<String> groupByFields,

        @JsonProperty("default_group_by")
        String defaultGroupBy
) {
}
