package com.demo.insight.healthindex;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.healthindex.dto.HealthIndexPointDto;
import com.demo.insight.healthindex.dto.HealthIndexRunDto;
import com.demo.insight.healthindex.dto.HealthIndexSummaryDto;
import com.demo.insight.healthindex.dto.HealthIndexTrendResponseDto;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
public class HealthIndexServiceImpl implements HealthIndexService {

    private static final int DEFAULT_RUN_LIMIT = 120;
    private static final List<String> ALLOWED_STATUSES = List.of("NORMAL", "WARNING", "CRITICAL");

    private final HealthIndexRepository healthIndexRepository;
    private final DynamicSchemaResolver schemaResolver;

    public HealthIndexServiceImpl(
            HealthIndexRepository healthIndexRepository,
            DynamicSchemaResolver schemaResolver
    ) {
        this.healthIndexRepository = healthIndexRepository;
        this.schemaResolver = schemaResolver;
    }

    @Override
    public List<HealthIndexRunDto> getHealthIndexRuns() {
        List<Document> runRows = healthIndexRepository.findRecentRunsWithAnomalyResults(DEFAULT_RUN_LIMIT);
        List<HealthIndexRunDto> runs = new ArrayList<>();

        for (Document row : runRows) {
            String runId = normalizeOptionalText(row.get("run_id"));
            if (runId == null) {
                continue;
            }

            String datasetKey = schemaResolver.normalizeDatasetKeyString(row.get("dataset_key"));
            String algoCode = normalizeOptionalText(row.get("algo_code"));
            String algoName = firstNonBlank(
                    normalizeOptionalText(row.get("algo_name")),
                    defaultAlgoName(algoCode)
            );

            String label = runId + " (" + (algoName == null ? "Unknown" : algoName) + ")";
            runs.add(new HealthIndexRunDto(
                    runId,
                    datasetKey,
                    algoCode,
                    algoName,
                    label,
                    normalizeTimestamp(row.get("executed_at"))
            ));
        }

        return runs;
    }

    @Override
    public HealthIndexTrendResponseDto getHealthIndexTrend(
            String runId,
            String datasetKey,
            String status,
            String from,
            String to
    ) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        String normalizedDatasetKey = normalizeRequiredText(datasetKey, "datasetKey");
        String normalizedStatus = resolveStatusFilter(status);
        Date fromDate = parseOptionalDate(from, "from");
        Date toDate = parseOptionalDate(to, "to");

        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            throw new IllegalArgumentException("from must be less than or equal to to.");
        }

        List<Document> rows = healthIndexRepository.findHealthIndexPoints(
                new HealthIndexRepository.TrendQuery(
                        normalizedRunId,
                        normalizedDatasetKey,
                        normalizedStatus,
                        fromDate,
                        toDate
                )
        );

        List<HealthIndexPointDto> points = new ArrayList<>(rows.size());
        for (Document row : rows) {
            points.add(toPoint(normalizedRunId, normalizedDatasetKey, row));
        }

        HealthIndexSummaryDto summary = buildSummary(points);
        return new HealthIndexTrendResponseDto(
                normalizedRunId,
                normalizedDatasetKey,
                summary,
                List.copyOf(points)
        );
    }

    private HealthIndexPointDto toPoint(String runId, String datasetKey, Document row) {
        Double healthIndex = asDouble(row.get("health_index"));
        Double healthIndexPercent = toHealthIndexPercent(healthIndex);
        return new HealthIndexPointDto(
                firstNonBlank(normalizeOptionalText(row.get("run_id")), runId),
                firstNonBlank(normalizeOptionalText(row.get("dataset_key")), datasetKey),
                firstNonBlank(
                        normalizeOptionalText(row.get("equipment_id")),
                        normalizeOptionalText(row.get("MCCODE"))
                ),
                normalizeTimestamp(row.get("window_start")),
                normalizeTimestamp(row.get("window_end")),
                healthIndex,
                healthIndexPercent,
                asDouble(row.get("anomaly_score")),
                normalizeStatus(row.get("status")),
                asBoolean(row.get("is_anomaly")),
                normalizeTimestamp(row.get("reg_date"))
        );
    }

    private HealthIndexSummaryDto buildSummary(List<HealthIndexPointDto> points) {
        if (points == null || points.isEmpty()) {
            return new HealthIndexSummaryDto(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null
            );
        }

        long totalCount = points.size();
        long normalCount = 0;
        long warningCount = 0;
        long criticalCount = 0;

        double sumPercent = 0D;
        int percentCount = 0;
        Double minPercent = null;
        Double maxPercent = null;

        for (HealthIndexPointDto point : points) {
            String status = normalizeStatus(point.status());
            if ("NORMAL".equals(status)) {
                normalCount++;
            } else if ("WARNING".equals(status)) {
                warningCount++;
            } else if ("CRITICAL".equals(status)) {
                criticalCount++;
            }

            Double percent = point.healthIndexPercent();
            if (percent != null && Double.isFinite(percent)) {
                sumPercent += percent;
                percentCount++;
                minPercent = minPercent == null ? percent : Math.min(minPercent, percent);
                maxPercent = maxPercent == null ? percent : Math.max(maxPercent, percent);
            }
        }

        HealthIndexPointDto latestPoint = points.get(points.size() - 1);
        Double avgPercent = percentCount == 0 ? null : (sumPercent / percentCount);

        return new HealthIndexSummaryDto(
                latestPoint.healthIndex(),
                latestPoint.healthIndexPercent(),
                avgPercent,
                minPercent,
                maxPercent,
                latestPoint.status(),
                normalCount,
                warningCount,
                criticalCount,
                totalCount,
                latestPoint.windowStart(),
                latestPoint.windowEnd()
        );
    }

    private String resolveStatusFilter(String status) {
        String normalized = normalizeOptionalText(status);
        if (normalized == null) {
            return null;
        }

        String upperStatus = normalized.toUpperCase(Locale.ROOT);
        if ("ALL".equals(upperStatus)) {
            return null;
        }
        if (!ALLOWED_STATUSES.contains(upperStatus)) {
            throw new IllegalArgumentException("status must be one of ALL, NORMAL, WARNING, CRITICAL.");
        }
        return upperStatus;
    }

    private String normalizeStatus(Object value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private Double toHealthIndexPercent(Double healthIndex) {
        if (healthIndex == null || !Double.isFinite(healthIndex)) {
            return null;
        }
        if (healthIndex >= 0D && healthIndex <= 1D) {
            return healthIndex * 100D;
        }
        if (healthIndex > 1D && healthIndex <= 100D) {
            return healthIndex;
        }
        return healthIndex;
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
        }

        return normalized;
    }

    private String defaultAlgoName(String algoCode) {
        String normalizedAlgoCode = normalizeOptionalText(algoCode);
        if (normalizedAlgoCode == null) {
            return "Unknown";
        }
        return switch (normalizedAlgoCode.toUpperCase(Locale.ROOT)) {
            case "ISOLATION_FOREST" -> "Isolation Forest";
            case "AUTOENCODER" -> "Autoencoder";
            default -> normalizedAlgoCode;
        };
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

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
            if (List.of("true", "y", "yes", "1").contains(normalized)) {
                return true;
            }
            if (List.of("false", "n", "no", "0").contains(normalized)) {
                return false;
            }
        }
        return null;
    }
}
