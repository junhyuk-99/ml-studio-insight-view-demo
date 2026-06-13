package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HistogramFieldOptionDto(
        String field,

        @JsonProperty("field_name")
        String fieldName,

        boolean numeric,

        @JsonProperty("sample_count")
        long sampleCount,

        @JsonProperty("non_null_count")
        long nonNullCount,

        @JsonProperty("null_count")
        long nullCount,

        @JsonProperty("has_value")
        boolean hasValue,

        @JsonProperty("default_selected")
        boolean defaultSelected,

        @JsonProperty("sequence_field")
        boolean sequenceField
) {
}
