package com.demo.insight.supervisedresult.dto;

import java.util.List;

public record SupervisedDistributionResponseDto(
        String runId,
        long totalCount,
        long tp,
        long tn,
        long fp,
        long fn,
        List<SupervisedDistributionItemDto> items
) {
}
