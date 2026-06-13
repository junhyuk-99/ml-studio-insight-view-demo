package com.demo.insight.dataexploration.dto;

public record TimeseriesPointDto(
        String timestamp,
        double value
) {
}
