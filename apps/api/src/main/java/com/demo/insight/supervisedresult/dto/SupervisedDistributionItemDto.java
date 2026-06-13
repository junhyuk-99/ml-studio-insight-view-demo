package com.demo.insight.supervisedresult.dto;

public record SupervisedDistributionItemDto(
        String errorType,
        long count,
        double ratio
) {
}
