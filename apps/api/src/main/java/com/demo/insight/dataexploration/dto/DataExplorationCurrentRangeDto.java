package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DataExplorationCurrentRangeDto(
        @JsonProperty("from")
        String from,

        @JsonProperty("to")
        String to
) {
}
