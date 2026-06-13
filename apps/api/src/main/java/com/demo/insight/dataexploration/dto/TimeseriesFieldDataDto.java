package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TimeseriesFieldDataDto(
        String field,

        @JsonProperty("sample_count")
        long sampleCount,
        Double min,
        Double max,
        Double avg,
        List<TimeseriesPointDto> points
) {
}
