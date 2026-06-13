package com.demo.insight.healthindex.dto;

public record HealthIndexSummaryDto(
        Double latestHealthIndex,
        Double latestHealthIndexPercent,
        Double avgHealthIndexPercent,
        Double minHealthIndexPercent,
        Double maxHealthIndexPercent,
        String latestStatus,
        long normalCount,
        long warningCount,
        long criticalCount,
        long totalCount,
        String latestWindowStart,
        String latestWindowEnd
) {
}
