package com.demo.insight.thresholdalert.dto;

public record ThresholdRuleDto(
        String ruleId,
        String datasetKey,
        String targetCollection,
        String targetType,
        String targetField,
        String displayName,
        String operator,
        Double warningValue,
        Double criticalValue,
        String valueScale,
        String severityOrder
) {
}
