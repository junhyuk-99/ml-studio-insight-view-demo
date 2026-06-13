package com.demo.insight.thresholdalert.dto;

import java.util.List;

public record ThresholdAlertListResponseDto(
        List<ThresholdAlertListItemDto> items,
        long total,
        int page,
        int size,
        int totalPages
) {
}
