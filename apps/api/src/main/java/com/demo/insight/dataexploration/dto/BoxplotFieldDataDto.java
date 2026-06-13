package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BoxplotFieldDataDto(
        String field,

        @JsonProperty("sample_count")
        long sampleCount,
        Double min,
        Double q1,
        Double median,
        Double q3,
        Double max,

        @JsonProperty("whisker_low")
        Double whiskerLow,

        @JsonProperty("whisker_high")
        Double whiskerHigh,

        @JsonProperty("outlier_count")
        long outlierCount,
        List<Double> outliers
) {
}
