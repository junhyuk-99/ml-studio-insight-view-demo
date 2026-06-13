package com.demo.insight.thresholdalert;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.equipment.service.EquipmentMasterService;
import com.demo.insight.thresholdalert.dto.ThresholdAlertBackfillResultDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertListItemDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertListResponseDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertRecalculateRunResultDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertSummaryDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertTrendPointDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertTrendResponseDto;
import com.demo.insight.thresholdalert.dto.ThresholdRuleDto;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ThresholdAlertServiceImpl implements ThresholdAlertService {

    private static final Logger log = LoggerFactory.getLogger(ThresholdAlertServiceImpl.class);

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;
    private static final int DEFAULT_BACKFILL_LIMIT = 200;
    private static final int DEFAULT_TREND_LIMIT = 500;
    private static final int MAX_TREND_LIMIT = 500;

    private static final String TARGET_COLLECTION_ANOMALY_RESULT = "thisanomalyresult";
    private static final String TARGET_TYPE_HEALTH_INDEX = "HEALTH_INDEX";
    private static final String TARGET_FIELD_HEALTH_INDEX = "health_index";

    private static final String SEVERITY_WARNING = "WARNING";
    private static final String SEVERITY_CRITICAL = "CRITICAL";

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_ACK = "ACK";
    private static final String STATUS_CLOSED = "CLOSED";

    private static final List<String> ALLOWED_SEVERITIES = List.of(SEVERITY_WARNING, SEVERITY_CRITICAL);
    private static final List<String> ALLOWED_STATUSES = List.of(STATUS_OPEN, STATUS_ACK, STATUS_CLOSED);
    private static final List<String> ALLOWED_ACK = List.of("y", "n");

    private final ThresholdAlertRepository thresholdAlertRepository;
    private final DynamicSchemaResolver schemaResolver;
    private final EquipmentMasterService equipmentMasterService;

    public ThresholdAlertServiceImpl(
            ThresholdAlertRepository thresholdAlertRepository,
            DynamicSchemaResolver schemaResolver,
            EquipmentMasterService equipmentMasterService
    ) {
        this.thresholdAlertRepository = thresholdAlertRepository;
        this.schemaResolver = schemaResolver;
        this.equipmentMasterService = equipmentMasterService;
    }

    @Override
    public ThresholdAlertListResponseDto getThresholdAlertList(
            String datasetKey,
            String runId,
            String severity,
            String status,
            String ackYn,
            String from,
            String to,
            Integer page,
            Integer size
    ) {
        String normalizedDatasetKey = normalizeRequiredDatasetKey(datasetKey, "datasetKey");
        String normalizedRunId = normalizeOptionalText(runId);
        String normalizedSeverity = resolveSeverityFilter(severity);
        String normalizedStatus = resolveStatusFilter(status);
        String normalizedAckYn = resolveAckFilter(ackYn);
        Date fromDate = parseOptionalDate(from, "from");
        Date toDate = parseOptionalDate(to, "to");

        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            throw new IllegalArgumentException("from must be less than or equal to to.");
        }

        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size);
        int skip = resolvedPage * resolvedSize;

        ThresholdAlertRepository.AlertListQuery listQuery = new ThresholdAlertRepository.AlertListQuery(
                normalizedDatasetKey,
                normalizedRunId,
                normalizedSeverity,
                normalizedStatus,
                normalizedAckYn,
                fromDate,
                toDate
        );

        long total = thresholdAlertRepository.countAlerts(listQuery);
        List<ThresholdAlertListItemDto> items = List.of();

        if (total > 0L && skip < total) {
            List<Document> rows = thresholdAlertRepository.findAlerts(listQuery, skip, resolvedSize);
            items = rows.stream().map(this::toListItem).toList();
        }

        int totalPages = total == 0L ? 0 : (int) Math.ceil(total / (double) resolvedSize);
        return new ThresholdAlertListResponseDto(
                items,
                total,
                resolvedPage,
                resolvedSize,
                totalPages
        );
    }

    @Override
    public ThresholdAlertSummaryDto getThresholdAlertSummary(
            String datasetKey,
            String runId,
            String from,
            String to
    ) {
        String normalizedDatasetKey = normalizeRequiredDatasetKey(datasetKey, "datasetKey");
        String normalizedRunId = normalizeOptionalText(runId);
        Date fromDate = parseOptionalDate(from, "from");
        Date toDate = parseOptionalDate(to, "to");

        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            throw new IllegalArgumentException("from must be less than or equal to to.");
        }

        ThresholdAlertRepository.AlertSummaryQuery summaryQuery = new ThresholdAlertRepository.AlertSummaryQuery(
                normalizedDatasetKey,
                normalizedRunId,
                fromDate,
                toDate
        );

        Document summary = thresholdAlertRepository.aggregateAlertSummary(summaryQuery);
        Document latest = thresholdAlertRepository.findLatestAlert(summaryQuery);

        long totalCount = asLong(summary == null ? null : summary.get("total_count"));
        long openCount = asLong(summary == null ? null : summary.get("open_count"));
        long ackCount = asLong(summary == null ? null : summary.get("ack_count"));
        long warningCount = asLong(summary == null ? null : summary.get("warning_count"));
        long criticalCount = asLong(summary == null ? null : summary.get("critical_count"));

        String latestAlertAt = normalizeTimestamp(latest == null ? null : latest.get("created_at"));
        if (latestAlertAt == null) {
            latestAlertAt = normalizeTimestamp(summary == null ? null : summary.get("latest_alert_at"));
        }

        String latestSeverity = normalizeUpperText(latest == null ? null : latest.get("severity"));
        String latestDisplayName = normalizeOptionalText(latest == null ? null : latest.get("display_name"));

        return new ThresholdAlertSummaryDto(
                totalCount,
                openCount,
                ackCount,
                warningCount,
                criticalCount,
                latestAlertAt,
                latestSeverity,
                latestDisplayName
        );
    }

    @Override
    public ThresholdAlertTrendResponseDto getThresholdAlertTrend(
            String datasetKey,
            String runId,
            String severity,
            String status,
            String ackYn,
            String from,
            String to,
            Integer limit
    ) {
        String normalizedDatasetKey = normalizeRequiredDatasetKey(datasetKey, "datasetKey");
        String normalizedRunId = normalizeOptionalText(runId);
        String normalizedSeverity = resolveSeverityFilter(severity);
        String normalizedStatus = resolveStatusFilter(status);
        String normalizedAckYn = resolveAckFilter(ackYn);
        Date fromDate = parseOptionalDate(from, "from");
        Date toDate = parseOptionalDate(to, "to");

        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            throw new IllegalArgumentException("from must be less than or equal to to.");
        }

        int resolvedLimit = resolveTrendLimit(limit);
        ThresholdAlertRepository.AlertListQuery trendQuery = new ThresholdAlertRepository.AlertListQuery(
                normalizedDatasetKey,
                normalizedRunId,
                normalizedSeverity,
                normalizedStatus,
                normalizedAckYn,
                fromDate,
                toDate
        );

        List<Document> rows = thresholdAlertRepository.findTrend(trendQuery, resolvedLimit);
        List<ThresholdAlertTrendPointDto> points = new ArrayList<>();

        for (Document row : rows) {
            ThresholdAlertTrendPointDto point = toTrendPoint(row);
            if (point != null) {
                points.add(point);
            }
        }

        return new ThresholdAlertTrendResponseDto(
                List.copyOf(points),
                points.size(),
                resolvedLimit
        );
    }

    @Override
    public ThresholdAlertRecalculateRunResultDto recalculateRun(String runId, String datasetKey) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        String normalizedDatasetKey = normalizeRequiredDatasetKey(datasetKey, "datasetKey");

        List<Document> targetRows = thresholdAlertRepository.findAnomalyResultsByRunAndDataset(
                normalizedRunId,
                normalizedDatasetKey
        );
        if (targetRows.isEmpty()) {
            return new ThresholdAlertRecalculateRunResultDto(
                    normalizedRunId,
                    normalizedDatasetKey,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        List<ThresholdRuleDto> rules = toThresholdRules(
                thresholdAlertRepository.findActiveHealthIndexRules(normalizedDatasetKey)
        );
        if (rules.isEmpty()) {
            return new ThresholdAlertRecalculateRunResultDto(
                    normalizedRunId,
                    normalizedDatasetKey,
                    targetRows.size(),
                    0,
                    targetRows.size(),
                    0,
                    0
            );
        }

        int processedCount = 0;
        int createdOrUpdatedCount = 0;
        int skippedCount = 0;
        int warningCount = 0;
        int criticalCount = 0;

        for (Document row : targetRows) {
            processedCount++;

            Date windowStart = asDate(row.get("window_start"));
            Date windowEnd = asDate(row.get("window_end"));
            if (windowStart == null || windowEnd == null) {
                skippedCount++;
                continue;
            }

            boolean createdForWindow = false;
            for (ThresholdRuleDto rule : rules) {
                try {
                    AlertEvaluation evaluation = evaluateRule(
                            rule,
                            row,
                            normalizedRunId,
                            normalizedDatasetKey,
                            windowStart,
                            windowEnd
                    );

                    if (evaluation.alertDocument() == null) {
                        continue;
                    }

                    thresholdAlertRepository.upsertThresholdAlert(evaluation.alertDocument());
                    createdOrUpdatedCount++;
                    createdForWindow = true;

                    if (SEVERITY_CRITICAL.equals(evaluation.severity())) {
                        criticalCount++;
                    } else if (SEVERITY_WARNING.equals(evaluation.severity())) {
                        warningCount++;
                    }
                } catch (Exception exception) {
                    log.warn(
                            "Threshold alert upsert failed. runId={}, datasetKey={}, ruleId={}, windowStart={}, windowEnd={}",
                            normalizedRunId,
                            normalizedDatasetKey,
                            rule.ruleId(),
                            normalizeTimestamp(windowStart),
                            normalizeTimestamp(windowEnd),
                            exception
                    );
                }
            }

            if (!createdForWindow) {
                skippedCount++;
            }
        }

        return new ThresholdAlertRecalculateRunResultDto(
                normalizedRunId,
                normalizedDatasetKey,
                processedCount,
                createdOrUpdatedCount,
                skippedCount,
                warningCount,
                criticalCount
        );
    }

    @Override
    public ThresholdAlertListItemDto ackAlert(String alertId, String ackBy, String memo) {
        String normalizedAlertId = normalizeRequiredText(alertId, "alertId");
        String normalizedAckBy = normalizeRequiredText(ackBy, "ackBy");
        String normalizedMemo = normalizeOptionalText(memo);

        Date now = new Date();
        Document updated = thresholdAlertRepository.ackAlert(
                normalizedAlertId,
                normalizedAckBy,
                normalizedMemo,
                now
        );

        if (updated == null) {
            throw new IllegalArgumentException("alertId not found.");
        }

        return toListItem(updated);
    }

    @Override
    public ThresholdAlertBackfillResultDto backfillMissingAlerts(Integer limit) {
        int resolvedLimit = resolveBackfillLimit(limit);
        List<Document> targets = thresholdAlertRepository.findMissingHealthIndexAlertTargets(resolvedLimit);

        if (targets.isEmpty()) {
            return new ThresholdAlertBackfillResultDto(
                    resolvedLimit,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        Map<String, List<ThresholdRuleDto>> rulesByDataset = new LinkedHashMap<>();

        for (Document target : targets) {
            processedCount++;
            String runId = normalizeOptionalText(target.get("run_id"));
            String datasetKey = canonicalizeDatasetKey(schemaResolver.normalizeDatasetKeyString(target.get("dataset_key")));
            Date windowStart = asDate(target.get("window_start"));
            Date windowEnd = asDate(target.get("window_end"));

            if (runId == null || datasetKey == null || windowStart == null || windowEnd == null) {
                skippedCount++;
                continue;
            }

            try {
                List<ThresholdRuleDto> rules = rulesByDataset.computeIfAbsent(datasetKey, key ->
                        toThresholdRules(thresholdAlertRepository.findActiveHealthIndexRules(key))
                );

                if (rules.isEmpty()) {
                    skippedCount++;
                    continue;
                }

                boolean createdForWindow = false;
                for (ThresholdRuleDto rule : rules) {
                    AlertEvaluation evaluation = evaluateRule(
                            rule,
                            target,
                            runId,
                            datasetKey,
                            windowStart,
                            windowEnd
                    );

                    if (evaluation.alertDocument() == null) {
                        continue;
                    }

                    thresholdAlertRepository.upsertThresholdAlert(evaluation.alertDocument());
                    createdForWindow = true;
                }

                if (createdForWindow) {
                    successCount++;
                } else {
                    skippedCount++;
                }
            } catch (Exception exception) {
                failureCount++;
                log.warn(
                        "Threshold alert backfill failed. runId={}, datasetKey={}, windowStart={}, windowEnd={}",
                        runId,
                        datasetKey,
                        normalizeTimestamp(windowStart),
                        normalizeTimestamp(windowEnd),
                        exception
                );
            }
        }

        return new ThresholdAlertBackfillResultDto(
                resolvedLimit,
                targets.size(),
                processedCount,
                successCount,
                failureCount,
                skippedCount
        );
    }

    private AlertEvaluation evaluateRule(
            ThresholdRuleDto rule,
            Document sourceRow,
            String runId,
            String datasetKey,
            Date windowStart,
            Date windowEnd
    ) {
        if (rule == null || normalizeOptionalText(rule.ruleId()) == null) {
            return AlertEvaluation.skipped();
        }

        Double value = resolveNumericByPath(sourceRow, rule.targetField());
        if (value == null || !Double.isFinite(value)) {
            return AlertEvaluation.skipped();
        }

        String severity = evaluateSeverity(
                value,
                rule.warningValue(),
                rule.criticalValue(),
                rule.operator(),
                rule.severityOrder()
        );
        if (severity == null) {
            return AlertEvaluation.skipped();
        }

        Date now = new Date();
        String displayName = normalizeOptionalText(rule.displayName());
        if (displayName == null) {
            displayName = normalizeOptionalText(rule.targetField());
        }
        String equipmentId = resolveEquipmentIdFromRow(sourceRow);

        Document alertDocument = new Document("alert_id", buildAlertId(runId, rule.ruleId(), windowStart))
                .append("rule_id", rule.ruleId())
                .append("dataset_key", datasetKey)
                .append("run_id", runId)
                .append("window_start", windowStart)
                .append("window_end", windowEnd)
                .append("target_collection", firstNonBlank(rule.targetCollection(), TARGET_COLLECTION_ANOMALY_RESULT))
                .append("target_type", firstNonBlank(rule.targetType(), TARGET_TYPE_HEALTH_INDEX))
                .append("target_field", firstNonBlank(rule.targetField(), TARGET_FIELD_HEALTH_INDEX))
                .append("display_name", displayName)
                .append("value", value)
                .append("severity", severity)
                .append("operator", normalizeOptionalText(rule.operator()))
                .append("warning_value", rule.warningValue())
                .append("critical_value", rule.criticalValue())
                .append("status", STATUS_OPEN)
                .append("ack_yn", "n")
                .append("created_at", now)
                .append("updated_at", now);
        if (equipmentId != null) {
            alertDocument.append("equipment_id", equipmentId);
            alertDocument.append("MCCODE", equipmentId);
        }

        return new AlertEvaluation(alertDocument, severity);
    }

    private List<ThresholdRuleDto> toThresholdRules(List<Document> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<ThresholdRuleDto> rules = new ArrayList<>();

        for (Document row : rows) {
            String ruleId = normalizeOptionalText(row.get("rule_id"));
            if (ruleId == null) {
                continue;
            }

            rules.add(new ThresholdRuleDto(
                    ruleId,
                    schemaResolver.normalizeDatasetKeyString(row.get("dataset_key")),
                    normalizeOptionalText(row.get("target_collection")),
                    normalizeUpperText(row.get("target_type")),
                    normalizeOptionalText(row.get("target_field")),
                    normalizeOptionalText(row.get("display_name")),
                    normalizeOptionalText(row.get("operator")),
                    asDouble(row.get("warning_value")),
                    asDouble(row.get("critical_value")),
                    normalizeUpperText(row.get("value_scale")),
                    normalizeUpperText(row.get("severity_order"))
            ));
        }

        return List.copyOf(rules);
    }

    private ThresholdAlertListItemDto toListItem(Document row) {
        String ackYn = normalizeAckYn(row.get("ack_yn"), row.get("ack_by"), row.get("ack_at"));
        String status = normalizeStatus(row.get("status"), ackYn);

        return new ThresholdAlertListItemDto(
                normalizeOptionalText(row.get("alert_id")),
                normalizeOptionalText(row.get("rule_id")),
                schemaResolver.normalizeDatasetKeyString(row.get("dataset_key")),
                normalizeOptionalText(row.get("run_id")),
                normalizeTimestamp(row.get("window_start")),
                normalizeTimestamp(row.get("window_end")),
                normalizeUpperText(row.get("target_type")),
                normalizeOptionalText(row.get("target_field")),
                normalizeOptionalText(row.get("display_name")),
                asDouble(row.get("value")),
                normalizeUpperText(row.get("severity")),
                normalizeOptionalText(row.get("operator")),
                asDouble(row.get("warning_value")),
                asDouble(row.get("critical_value")),
                status,
                ackYn,
                normalizeOptionalText(row.get("ack_by")),
                normalizeTimestamp(row.get("ack_at")),
                normalizeOptionalText(row.get("memo")),
                normalizeTimestamp(row.get("created_at")),
                normalizeTimestamp(row.get("updated_at"))
        );
    }

    private ThresholdAlertTrendPointDto toTrendPoint(Document row) {
        Double value = asDouble(row.get("value"));
        if (value == null || !Double.isFinite(value)) {
            return null;
        }

        Double valuePercent = toPercentForTrend(value, row);
        if (valuePercent == null || !Double.isFinite(valuePercent)) {
            return null;
        }

        Double warningValue = asDouble(row.get("warning_value"));
        Double criticalValue = asDouble(row.get("critical_value"));
        String ackYn = normalizeAckYn(row.get("ack_yn"), row.get("ack_by"), row.get("ack_at"));
        String status = normalizeStatus(row.get("status"), ackYn);

        return new ThresholdAlertTrendPointDto(
                normalizeOptionalText(row.get("alert_id")),
                schemaResolver.normalizeDatasetKeyString(row.get("dataset_key")),
                normalizeOptionalText(row.get("run_id")),
                normalizeTimestamp(row.get("window_start")),
                normalizeTimestamp(row.get("window_end")),
                normalizeTimestamp(row.get("created_at")),
                normalizeOptionalText(row.get("display_name")),
                normalizeOptionalText(row.get("target_field")),
                value,
                valuePercent,
                normalizeUpperText(row.get("severity")),
                warningValue,
                toPercentForTrend(warningValue, row),
                criticalValue,
                toPercentForTrend(criticalValue, row),
                status,
                ackYn
        );
    }

    private String resolveSeverityFilter(String severity) {
        String normalized = normalizeOptionalText(severity);
        if (normalized == null) {
            return null;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("ALL".equals(upper)) {
            return null;
        }
        if (!ALLOWED_SEVERITIES.contains(upper)) {
            throw new IllegalArgumentException("severity must be one of ALL, WARNING, CRITICAL.");
        }
        return upper;
    }

    private String resolveStatusFilter(String status) {
        String normalized = normalizeOptionalText(status);
        if (normalized == null) {
            return null;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("ALL".equals(upper)) {
            return null;
        }
        if (!ALLOWED_STATUSES.contains(upper)) {
            throw new IllegalArgumentException("status must be one of ALL, OPEN, ACK, CLOSED.");
        }
        return upper;
    }

    private String resolveAckFilter(String ackYn) {
        String normalized = normalizeOptionalText(ackYn);
        if (normalized == null) {
            return null;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("all".equals(lower)) {
            return null;
        }
        if (!ALLOWED_ACK.contains(lower)) {
            throw new IllegalArgumentException("ackYn must be one of ALL, y, n.");
        }
        return lower;
    }

    private String evaluateSeverity(
            Double value,
            Double warningValue,
            Double criticalValue,
            String operator,
            String severityOrder
    ) {
        if (value == null || warningValue == null || criticalValue == null) {
            return null;
        }
        if (!Double.isFinite(value) || !Double.isFinite(warningValue) || !Double.isFinite(criticalValue)) {
            return null;
        }

        String resolvedOrder = normalizeUpperText(severityOrder);
        if (resolvedOrder == null) {
            resolvedOrder = resolveOrderFromOperator(operator);
        }

        if ("LOWER_IS_WORSE".equals(resolvedOrder)) {
            double criticalBoundary = Math.min(warningValue, criticalValue);
            double warningBoundary = Math.max(warningValue, criticalValue);
            if (value <= criticalBoundary) {
                return SEVERITY_CRITICAL;
            }
            if (value <= warningBoundary) {
                return SEVERITY_WARNING;
            }
            return null;
        }

        if ("HIGHER_IS_WORSE".equals(resolvedOrder)) {
            double criticalBoundary = Math.max(warningValue, criticalValue);
            double warningBoundary = Math.min(warningValue, criticalValue);
            if (value >= criticalBoundary) {
                return SEVERITY_CRITICAL;
            }
            if (value >= warningBoundary) {
                return SEVERITY_WARNING;
            }
            return null;
        }

        return null;
    }

    private String resolveOrderFromOperator(String operator) {
        String normalizedOperator = normalizeUpperText(operator);
        if (normalizedOperator == null) {
            return "LOWER_IS_WORSE";
        }

        return switch (normalizedOperator) {
            case "LT", "LTE", "LE", "LESS_THAN" -> "LOWER_IS_WORSE";
            case "GT", "GTE", "GE", "GREATER_THAN" -> "HIGHER_IS_WORSE";
            default -> "LOWER_IS_WORSE";
        };
    }

    private Double resolveNumericByPath(Document source, String path) {
        String normalizedPath = normalizeOptionalText(path);
        if (source == null || normalizedPath == null) {
            return null;
        }

        Object current = source;
        String[] tokens = normalizedPath.split("\\.");
        for (String token : tokens) {
            if (token.isBlank()) {
                return null;
            }

            if (current instanceof Document documentValue) {
                current = documentValue.get(token);
                continue;
            }
            if (current instanceof Map<?, ?> mapValue) {
                current = mapValue.get(token);
                continue;
            }
            return null;
        }

        return asDouble(current);
    }

    private int resolvePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private int resolveBackfillLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_BACKFILL_LIMIT;
        }
        return Math.min(limit, MAX_SIZE);
    }

    private int resolveTrendLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TREND_LIMIT;
        }
        return Math.min(limit, MAX_TREND_LIMIT);
    }

    private String normalizeRequiredDatasetKey(String value, String fieldName) {
        String normalized = schemaResolver.normalizeDatasetKeyString(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String canonical = canonicalizeDatasetKey(normalized);
        if (canonical == null) {
            throw new IllegalArgumentException("Deprecated dataset_key is not allowed for threshold alert runtime.");
        }
        return canonical;
    }

    private String canonicalizeDatasetKey(String datasetKey) {
        String normalized = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalized == null) {
            return null;
        }
        if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeOptionalText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeUpperText(Object value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String resolveEquipmentIdFromRow(Document row) {
        if (row == null) {
            return null;
        }
        String equipmentId = normalizeOptionalText(row.get("equipment_id"));
        if (equipmentId == null) {
            equipmentId = normalizeOptionalText(row.get("MCCODE"));
        }
        if (equipmentId == null) {
            return null;
        }
        return equipmentId.toUpperCase(Locale.ROOT);
    }

    private Double toPercentForTrend(Double value, Document row) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }

        String valueScale = normalizeUpperText(row.get("value_scale"));
        String targetType = normalizeUpperText(row.get("target_type"));
        String targetField = normalizeOptionalText(row.get("target_field"));

        boolean isHealthIndexScale = TARGET_TYPE_HEALTH_INDEX.equals(targetType)
                || (targetField != null && TARGET_FIELD_HEALTH_INDEX.equalsIgnoreCase(targetField));
        boolean isRatioScale = "RATIO_0_1".equals(valueScale) || isHealthIndexScale;

        if (isRatioScale && value >= 0d && value <= 1d) {
            return value * 100d;
        }

        return value;
    }

    private String normalizeStatus(Object statusValue, String ackYn) {
        String status = normalizeUpperText(statusValue);
        if (status != null) {
            return status;
        }
        return "y".equalsIgnoreCase(ackYn) ? STATUS_ACK : STATUS_OPEN;
    }

    private String normalizeAckYn(Object ackYn, Object ackBy, Object ackAt) {
        String normalized = normalizeOptionalText(ackYn);
        if (normalized != null) {
            return "y".equalsIgnoreCase(normalized) ? "y" : "n";
        }
        return ackBy != null || ackAt != null ? "y" : "n";
    }

    private Date parseOptionalDate(String rawDate, String fieldName) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return null;
        }
        return parseDate(rawDate, fieldName);
    }

    private Date parseDate(String rawDate, String fieldName) {
        String normalized = rawDate.trim();
        try {
            return Date.from(Instant.parse(normalized));
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDate localDate = LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
            return Date.from(localDate.atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException(fieldName + " must be a valid ISO-8601 date or date-time.");
    }

    private Date asDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date dateValue) {
            return dateValue;
        }
        if (value instanceof Instant instantValue) {
            return Date.from(instantValue);
        }
        if (value instanceof Number numberValue) {
            return new Date(numberValue.longValue());
        }

        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Date.from(Instant.parse(normalized));
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String normalizeTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date dateValue) {
            return dateValue.toInstant().toString();
        }
        if (value instanceof Instant instantValue) {
            return instantValue.toString();
        }
        if (value instanceof Number numberValue) {
            return Instant.ofEpochMilli(numberValue.longValue()).toString();
        }

        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Instant.parse(normalized).toString();
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return localDateTime.toInstant(ZoneOffset.UTC).toString();
        } catch (DateTimeParseException ignored) {
            return normalized;
        }
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }

        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }

        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return 0L;
            }
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }

        return 0L;
    }

    private String buildAlertId(String runId, String ruleId, Date windowStart) {
        long epochMillis = windowStart == null ? System.currentTimeMillis() : windowStart.getTime();
        return "ALERT_" + sanitizeToken(runId) + "_" + sanitizeToken(ruleId) + "_" + epochMillis;
    }

    private String sanitizeToken(String token) {
        String normalized = normalizeOptionalText(token);
        if (normalized == null) {
            return "UNKNOWN";
        }
        return normalized.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeOptionalText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private record AlertEvaluation(Document alertDocument, String severity) {
        private static AlertEvaluation skipped() {
            return new AlertEvaluation(null, null);
        }
    }
}
