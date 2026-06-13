package com.demo.insight.dataexploration.dto;

public record HistogramBinDto(
        int index,
        double start,
        double end,
        long count
) {
}
