package com.demo.insight.home.dto;

public record HomeCorrelationSummaryDto(
        int fieldCount,
        boolean available,
        String message
) {
}
