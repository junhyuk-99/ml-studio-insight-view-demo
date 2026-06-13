package com.demo.insight.supervisedresult;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.supervisedresult.dto.SupervisedDistributionItemDto;
import com.demo.insight.supervisedresult.dto.SupervisedDistributionResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedErrorRowDto;
import com.demo.insight.supervisedresult.dto.SupervisedErrorsResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedFeatureImportanceDto;
import com.demo.insight.supervisedresult.dto.SupervisedMetricDto;
import com.demo.insight.supervisedresult.dto.SupervisedPredictionPageResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedPredictionRowDto;
import com.demo.insight.supervisedresult.dto.SupervisedRunDto;
import com.demo.insight.supervisedresult.dto.SupervisedSummaryResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedTrendPointDto;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SupervisedResultServiceImpl implements SupervisedResultService {

    private static final int DEFAULT_RUN_LIMIT = 120;
    private static final int MAX_RUN_LIMIT = 300;
    private static final int DEFAULT_TREND_LIMIT = 10;
    private static final int MAX_TREND_LIMIT = 100;

    private static final int DEFAULT_ERROR_LIMIT = 5;
    private static final int MAX_ERROR_LIMIT = 50;

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;

    private static final List<String> ALLOWED_TRIGGER_TYPES = List.of("MANUAL", "SCHEDULE");
    private static final List<String> ALLOWED_PREDICTION_FILTERS = List.of(
            "ALL", "CORRECT", "INCORRECT", "TP", "TN", "FP", "FN"
    );
    private static final List<String> DISTRIBUTION_ORDER = List.of("TP", "TN", "FP", "FN");

    private final SupervisedResultRepository supervisedResultRepository;
    private final DynamicSchemaResolver schemaResolver;

    public SupervisedResultServiceImpl(
            SupervisedResultRepository supervisedResultRepository,
            DynamicSchemaResolver schemaResolver
    ) {
        this.supervisedResultRepository = supervisedResultRepository;
        this.schemaResolver = schemaResolver;
    }

    @Override
    public List<SupervisedRunDto> getRuns(String triggerType, Integer limit) {
        String normalizedTriggerType = resolveTriggerType(triggerType);
        int resolvedLimit = resolveRunLimit(limit);

        List<Document> rows = supervisedResultRepository.findSupervisedRuns(normalizedTriggerType, resolvedLimit);
        List<SupervisedRunDto> runs = new ArrayList<>();

        for (Document row : rows) {
            String runId = normalizeOptionalText(row.get("run_id"));
            if (runId == null) {
                continue;
            }

            String algoCode = firstNonBlank(normalizeUpperText(row.get("algo_code")), "RANDOM_FOREST");
            String algoName = firstNonBlank(normalizeOptionalText(row.get("algo_name")), "Random Forest");
            runs.add(new SupervisedRunDto(
                    runId,
                    schemaResolver.normalizeDatasetKeyString(row.get("dataset_key")),
                    algoCode,
                    algoName,
                    normalizeUpperText(row.get("status")),
                    normalizeUpperText(row.get("trigger_type")),
                    normalizeTimestamp(row.get("executed_at")),
                    asLong(row.get("total_predictions"))
            ));
        }

        return List.copyOf(runs);
    }

    @Override
    public List<SupervisedTrendPointDto> getTrend(String triggerType, Integer limit) {
        String normalizedTriggerType = resolveTriggerType(triggerType);
        int resolvedLimit = resolveTrendLimit(limit);

        List<Document> rows = supervisedResultRepository.findTrend(normalizedTriggerType, resolvedLimit);
        List<SupervisedTrendPointDto> points = new ArrayList<>();

        for (Document row : rows) {
            String runId = normalizeOptionalText(row.get("run_id"));
            if (runId == null) {
                continue;
            }

            points.add(new SupervisedTrendPointDto(
                    runId,
                    normalizeTimestamp(row.get("reg_date")),
                    normalizeUpperText(row.get("trigger_type")),
                    asDouble(row.get("accuracy")),
                    asDouble(row.get("precision")),
                    asDouble(row.get("recall")),
                    asDouble(row.get("f1_score"))
            ));
        }

        return List.copyOf(points);
    }

    @Override
    public SupervisedSummaryResponseDto getSummary(String runId) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        Document run = requireRun(normalizedRunId);
        Document modelEval = supervisedResultRepository.findLatestModelEvalByRunId(normalizedRunId);

        boolean hasModelEval = modelEval != null;
        SupervisedDistributionResponseDto distribution = hasModelEval ? null : getDistribution(normalizedRunId);
        long tp = hasModelEval ? asLong(modelEval.get("tp")) : distribution == null ? 0L : distribution.tp();
        long tn = hasModelEval ? asLong(modelEval.get("tn")) : distribution == null ? 0L : distribution.tn();
        long fp = hasModelEval ? asLong(modelEval.get("fp")) : distribution == null ? 0L : distribution.fp();
        long fn = hasModelEval ? asLong(modelEval.get("fn")) : distribution == null ? 0L : distribution.fn();

        long countFromMatrix = Math.max(tp + tn + fp + fn, 0L);
        long totalPredictions = hasModelEval
                ? firstPositive(
                asLong(modelEval.get("test_count")),
                asLong(modelEval.get("total_count")),
                countFromMatrix
        )
                : firstPositive(distribution == null ? 0L : distribution.totalCount(), countFromMatrix, 0L);

        SupervisedMetricDto accuracyMetric = buildMetric(
                "accuracy",
                "Accuracy",
                hasModelEval ? asDouble(modelEval.get("accuracy")) : null,
                tp + tn,
                totalPredictions
        );
        SupervisedMetricDto precisionMetric = buildMetric(
                "precision",
                "Precision",
                hasModelEval ? asDouble(modelEval.get("precision")) : null,
                tp,
                tp + fp
        );
        SupervisedMetricDto recallMetric = buildMetric(
                "recall",
                "Recall",
                hasModelEval ? asDouble(modelEval.get("recall")) : null,
                tp,
                tp + fn
        );
        SupervisedMetricDto f1Metric = buildMetric(
                "f1Score",
                "F1 Score",
                hasModelEval ? asDouble(modelEval.get("f1_score")) : null,
                2L * tp,
                (2L * tp) + fp + fn
        );

        return new SupervisedSummaryResponseDto(
                normalizedRunId,
                firstNonBlank(
                        schemaResolver.normalizeDatasetKeyString(run.get("dataset_key")),
                        schemaResolver.normalizeDatasetKeyString(hasModelEval ? modelEval.get("dataset_key") : null)
                ),
                firstNonBlank(normalizeUpperText(run.get("algo_code")), "RANDOM_FOREST"),
                firstNonBlank(normalizeOptionalText(run.get("algo_name")), "Random Forest"),
                firstNonBlank(normalizeUpperText(run.get("status")), "SUCCESS"),
                normalizeUpperText(run.get("trigger_type")),
                normalizeTimestamp(firstNonNull(run.get("reg_date"), run.get("updated_at"))),
                totalPredictions,
                accuracyMetric,
                precisionMetric,
                recallMetric,
                f1Metric,
                tp,
                tn,
                fp,
                fn,
                extractFeatureImportances(modelEval)
        );
    }

    @Override
    public SupervisedDistributionResponseDto getDistribution(String runId) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        Document aggregate = supervisedResultRepository.aggregateDistributionByRunId(normalizedRunId);

        Map<String, Long> distributionCountByType = new LinkedHashMap<>();
        for (String key : DISTRIBUTION_ORDER) {
            distributionCountByType.put(key, 0L);
        }

        long totalCount = 0L;
        if (aggregate != null) {
            totalCount = asLong(aggregate.get("total_count"));
            for (Document item : castDocumentList(aggregate.get("items"))) {
                String errorType = normalizeUpperText(item.get("error_type"));
                if (errorType == null || !distributionCountByType.containsKey(errorType)) {
                    continue;
                }
                distributionCountByType.put(errorType, asLong(item.get("count")));
            }
        }

        if (totalCount <= 0L) {
            totalCount = distributionCountByType.values().stream().mapToLong(Long::longValue).sum();
        }

        List<SupervisedDistributionItemDto> items = new ArrayList<>();
        for (String errorType : DISTRIBUTION_ORDER) {
            long count = distributionCountByType.getOrDefault(errorType, 0L);
            items.add(new SupervisedDistributionItemDto(
                    errorType,
                    count,
                    safeRatio(count, totalCount)
            ));
        }

        return new SupervisedDistributionResponseDto(
                normalizedRunId,
                totalCount,
                distributionCountByType.getOrDefault("TP", 0L),
                distributionCountByType.getOrDefault("TN", 0L),
                distributionCountByType.getOrDefault("FP", 0L),
                distributionCountByType.getOrDefault("FN", 0L),
                List.copyOf(items)
        );
    }

    @Override
    public SupervisedErrorsResponseDto getErrors(String runId, Integer limit) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        int resolvedLimit = resolveErrorLimit(limit);

        List<SupervisedErrorRowDto> fpTop = supervisedResultRepository.findTopErrors(normalizedRunId, "FP", resolvedLimit)
                .stream()
                .map(this::toErrorRow)
                .toList();

        List<SupervisedErrorRowDto> fnTop = supervisedResultRepository.findTopErrors(normalizedRunId, "FN", resolvedLimit)
                .stream()
                .map(this::toErrorRow)
                .toList();

        return new SupervisedErrorsResponseDto(
                normalizedRunId,
                fpTop,
                fnTop
        );
    }

    @Override
    public SupervisedPredictionPageResponseDto getPredictions(
            String runId,
            String filter,
            String from,
            String to,
            Integer page,
            Integer size
    ) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        String normalizedFilter = resolvePredictionFilter(filter);
        Date fromDate = parseOptionalDate(from, "from");
        Date toDate = parseOptionalDate(to, "to");

        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            throw new IllegalArgumentException("from must be less than or equal to to.");
        }

        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size);

        SupervisedResultRepository.PredictionPageResult result = supervisedResultRepository.findPredictions(
                new SupervisedResultRepository.PredictionQuery(
                        normalizedRunId,
                        normalizedFilter,
                        fromDate,
                        toDate,
                        resolvedPage,
                        resolvedSize
                )
        );

        List<SupervisedPredictionRowDto> items = result.items().stream()
                .map(this::toPredictionRow)
                .toList();

        long total = result.total();
        int totalPages = total == 0L ? 0 : (int) Math.ceil(total / (double) resolvedSize);
        return new SupervisedPredictionPageResponseDto(
                items,
                total,
                resolvedPage,
                resolvedSize,
                totalPages
        );
    }

    private Document requireRun(String runId) {
        Document run = supervisedResultRepository.findRunByRunId(runId);
        if (run == null) {
            throw new IllegalArgumentException("runId not found.");
        }
        return run;
    }

    private SupervisedErrorRowDto toErrorRow(Document row) {
        return new SupervisedErrorRowDto(
                normalizeTimestamp(row.get("resolved_timestamp")),
                asInteger(row.get("actual_label")),
                asInteger(row.get("prediction_label")),
                asDouble(row.get("prediction_probability_anomaly")),
                asDouble(row.get("prediction_probability_normal")),
                asDouble(row.get("prediction_probability")),
                extractTopFeatures(row.get("input_features"), 3)
        );
    }

    private SupervisedPredictionRowDto toPredictionRow(Document row) {
        String correctYn = normalizeUpperText(row.get("correct_yn"));
        return new SupervisedPredictionRowDto(
                normalizeTimestamp(row.get("resolved_timestamp")),
                asInteger(row.get("actual_label")),
                asInteger(row.get("prediction_label")),
                asDouble(row.get("prediction_probability_anomaly")),
                asDouble(row.get("prediction_probability_normal")),
                asDouble(row.get("prediction_probability")),
                correctYn,
                normalizeUpperText(row.get("error_type")),
                extractTopFeatures(row.get("input_features"), 3)
        );
    }

    private SupervisedMetricDto buildMetric(
            String key,
            String label,
            Double valueFromEval,
            long numerator,
            long denominator
    ) {
        Double value = valueFromEval;
        if (value == null || !Double.isFinite(value)) {
            value = safeRatioNullable(numerator, denominator);
        }
        return new SupervisedMetricDto(key, label, value, numerator, denominator);
    }

    private List<String> extractTopFeatures(Object rawInputFeatures, int limit) {
        if (!(rawInputFeatures instanceof Map<?, ?> inputFeatureMap)) {
            return List.of();
        }

        List<FeatureMagnitude> magnitudes = new ArrayList<>();
        List<String> availableKeys = new ArrayList<>();

        for (Map.Entry<?, ?> entry : inputFeatureMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            String key = normalizeOptionalText(entry.getKey());
            if (key == null) {
                continue;
            }
            availableKeys.add(key);

            Double numericValue = asDouble(entry.getValue());
            if (numericValue == null || !Double.isFinite(numericValue)) {
                continue;
            }
            magnitudes.add(new FeatureMagnitude(key, Math.abs(numericValue)));
        }

        if (!magnitudes.isEmpty()) {
            return magnitudes.stream()
                    .sorted((left, right) -> {
                        int magnitudeCompare = Double.compare(right.magnitude(), left.magnitude());
                        if (magnitudeCompare != 0) {
                            return magnitudeCompare;
                        }
                        return left.featureName().compareTo(right.featureName());
                    })
                    .map(FeatureMagnitude::featureName)
                    .distinct()
                    .limit(limit)
                    .toList();
        }

        return availableKeys.stream().distinct().limit(limit).toList();
    }

    private List<SupervisedFeatureImportanceDto> extractFeatureImportances(Document modelEval) {
        if (modelEval == null) {
            return List.of();
        }
        Object rawFeatureImportances = modelEval.get("feature_importances");
        if (!(rawFeatureImportances instanceof List<?> rawList)) {
            return List.of();
        }

        List<SupervisedFeatureImportanceDto> items = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }

            String feature = normalizeOptionalText(row.get("feature"));
            if (feature == null) {
                continue;
            }
            Double importance = asDouble(row.get("importance"));
            if (importance == null || !Double.isFinite(importance)) {
                importance = 0D;
            }

            items.add(new SupervisedFeatureImportanceDto(
                    asInteger(row.get("rank")) == null ? 0 : Math.max(0, asInteger(row.get("rank"))),
                    feature,
                    importance
            ));
        }

        if (items.isEmpty()) {
            return List.of();
        }

        items.sort((left, right) -> {
            int leftRank = left.rank();
            int rightRank = right.rank();
            boolean leftHasRank = leftRank > 0;
            boolean rightHasRank = rightRank > 0;

            if (leftHasRank && rightHasRank) {
                int compareByRank = Integer.compare(leftRank, rightRank);
                if (compareByRank != 0) {
                    return compareByRank;
                }
            } else if (leftHasRank != rightHasRank) {
                return leftHasRank ? -1 : 1;
            }

            int compareByImportance = Double.compare(
                    right.importance() == null ? 0D : right.importance(),
                    left.importance() == null ? 0D : left.importance()
            );
            if (compareByImportance != 0) {
                return compareByImportance;
            }
            return left.feature().compareTo(right.feature());
        });

        List<SupervisedFeatureImportanceDto> ranked = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            SupervisedFeatureImportanceDto item = items.get(index);
            ranked.add(new SupervisedFeatureImportanceDto(
                    index + 1,
                    item.feature(),
                    item.importance()
            ));
        }
        return List.copyOf(ranked);
    }

    private String resolveTriggerType(String triggerType) {
        String normalized = normalizeOptionalText(triggerType);
        if (normalized == null) {
            return null;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("ALL".equals(upper)) {
            return null;
        }
        if (!ALLOWED_TRIGGER_TYPES.contains(upper)) {
            throw new IllegalArgumentException("triggerType must be one of ALL, MANUAL, SCHEDULE.");
        }
        return upper;
    }

    private String resolvePredictionFilter(String filter) {
        String normalized = normalizeOptionalText(filter);
        if (normalized == null) {
            return "ALL";
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_PREDICTION_FILTERS.contains(upper)) {
            throw new IllegalArgumentException("filter must be one of ALL, CORRECT, INCORRECT, TP, TN, FP, FN.");
        }
        return upper;
    }

    private int resolveRunLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RUN_LIMIT;
        }
        return Math.min(limit, MAX_RUN_LIMIT);
    }

    private int resolveTrendLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TREND_LIMIT;
        }
        return Math.min(limit, MAX_TREND_LIMIT);
    }

    private int resolveErrorLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_ERROR_LIMIT;
        }
        return Math.min(limit, MAX_ERROR_LIMIT);
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

    private String normalizeUpperText(Object value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
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

    private Integer asInteger(Object value) {
        Double numericValue = asDouble(value);
        return numericValue == null ? null : numericValue.intValue();
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

    private List<Document> castDocumentList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }

        List<Document> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Document document) {
                rows.add(document);
            }
        }
        return rows;
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

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private long firstPositive(long... values) {
        if (values == null) {
            return 0L;
        }
        for (long value : values) {
            if (value > 0L) {
                return value;
            }
        }
        return values.length == 0 ? 0L : values[0];
    }

    private double safeRatio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0D;
        }
        return numerator / (double) denominator;
    }

    private Double safeRatioNullable(long numerator, long denominator) {
        if (denominator <= 0L) {
            return null;
        }
        return numerator / (double) denominator;
    }

    private record FeatureMagnitude(
            String featureName,
            double magnitude
    ) {
    }
}
