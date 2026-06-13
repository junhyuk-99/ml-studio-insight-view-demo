package com.demo.insight.thresholdalert.dto;

import java.util.List;

public record ThresholdAlertTrendResponseDto(
        List<ThresholdAlertTrendPointDto> points,
        int totalReturned,
        int limit
) {
}
