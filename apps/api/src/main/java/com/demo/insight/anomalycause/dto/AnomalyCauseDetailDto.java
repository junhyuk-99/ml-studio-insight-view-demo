package com.demo.insight.anomalycause.dto;

import java.util.List;
import java.util.Map;

public record AnomalyCauseDetailDto(
        String runId,
        String datasetKey,
        String equipmentId,
        String windowStart,
        String windowEnd,
        Double anomalyScore,
        Double healthIndex,
        String anomalyStatus,
        String causeMethod,
        String causeVersion,
        BaselineScopeDto baselineScope,
        List<String> causeSummary,
        List<CauseCandidateDto> causeCandidates,
        List<GroupScoreDto> groupScores,
        Map<String, Object> sourceRef,
        boolean causeGenerated,
        String createdAt,
        String updatedAt
) {
}
