package com.demo.insight.home.dto;

import java.util.List;

public record HomeDashboardResponseDto(
        String syncedAt,
        HomeDashboardKpiDto kpi,
        List<HomeActiveModelDto> activeModels,
        List<HomeAnomalyTrendPointDto> anomalyTrend,
        HomeCorrelationSummaryDto correlationSummary,
        List<HomeRecentRunDto> recentRuns,
        HomeLatestSupervisedDto latestSupervised
) {
}
