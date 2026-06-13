package com.demo.insight.anomalycause.dto;

import java.util.List;

public record AnomalyCauseListResponseDto(
        List<AnomalyCauseListItemDto> items,
        long total,
        int page,
        int size,
        int totalPages
) {
}
