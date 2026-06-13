package com.demo.insight.supervisedresult.dto;

import java.util.List;

public record SupervisedPredictionPageResponseDto(
        List<SupervisedPredictionRowDto> items,
        long total,
        int page,
        int size,
        int totalPages
) {
}
