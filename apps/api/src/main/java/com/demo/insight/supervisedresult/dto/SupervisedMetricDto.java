package com.demo.insight.supervisedresult.dto;

public record SupervisedMetricDto(
        String key,
        String label,
        Double value,
        long numerator,
        long denominator
) {
}
