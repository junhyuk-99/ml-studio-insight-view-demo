package com.demo.insight.anomalycause;

import com.demo.insight.anomalycause.dto.AnomalyCauseBackfillResultDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseDetailDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseListItemDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseListResponseDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseRecalculateRunResultDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseRunDto;
import com.demo.insight.anomalycause.dto.BaselineScopeDto;
import com.demo.insight.anomalycause.dto.CauseCandidateDto;
import com.demo.insight.anomalycause.dto.GroupScoreDto;
import com.demo.insight.common.schema.DynamicSchemaResolver;
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
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AnomalyCauseServiceImpl implements AnomalyCauseService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyCauseServiceImpl.class);

    private static final int DEFAULT_RUN_LIMIT = 120;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 200;
    private static final int DEFAULT_BACKFILL_LIMIT = 200;
    private static final int DEFAULT_TOP_N = 10;
    private static final int SUMMARY_TOP_N = 3;
    private static final int MIN_BASELINE_SAMPLES = 5;
    private static final double BASELINE_STD_EPSILON = 1e-9D;

    private static final List<String> ALLOWED_STATUSES = List.of("NORMAL", "WARNING", "CRITICAL");
    private static final String STATUS_NORMAL = "NORMAL";
    private static final String DIRECTION_HIGH = "HIGH";
    private static final String DIRECTION_LOW = "LOW";
    private static final String DIRECTION_FLAT = "FLAT";
    private static final String DIRECTION_UNKNOWN = "UNKNOWN";
    private static final String CAUSE_METHOD = "baseline_zscore";
    private static final String CAUSE_VERSION = "v1.0";
    private static final String BASELINE_SCOPE_SAME_RUN = "same_run_normal";
    private static final String BASELINE_SCOPE_DATASET = "dataset_normal_fallback";
    private static final String BASELINE_SCOPE_INSUFFICIENT = "insufficient_normal_samples";
    private static final String FEATURE_STAT_MEAN = "mean";
    private static final String FEATURE_STAT_STD = "std";
    private static final String FEATURE_STAT_MIN = "min";
    private static final String FEATURE_STAT_MAX = "max";
    private static final List<String> SUPPORTED_STATS = List.of(
            FEATURE_STAT_MEAN,
            FEATURE_STAT_STD,
            FEATURE_STAT_MIN,
            FEATURE_STAT_MAX
    );
    private static final Set<String> EXCLUDED_SOURCE_FIELDS = Set.of(
            "_id",
            "id",
            "run_id",
            "window_start",
            "window_end",
            "prdtime",
            "regdate",
            "lastdate",
            "sync_date",
            "mccode",
            "equipment_id",
            "sensor_id",
            "timestamp",
            "reg_date",
            "workorder",
            "prddate",
            "source_db",
            "source_table",
            "ifflag",
            "useflag",
            "regemp",
            "lastemp",
            "remark1",
            "remark2",
            "recent_flag"
    );

    private final AnomalyCauseRepository anomalyCauseRepository;
    private final DynamicSchemaResolver schemaResolver;

    public AnomalyCauseServiceImpl(
            AnomalyCauseRepository anomalyCauseRepository,
            DynamicSchemaResolver schemaResolver
    ) {
        this.anomalyCauseRepository = anomalyCauseRepository;
        this.schemaResolver = schemaResolver;
    }

    @Override
    public List<AnomalyCauseRunDto> getAnomalyCauseRuns() {
        List<Document> runRows = anomalyCauseRepository.findRecentRunsWithAnomalyResults(DEFAULT_RUN_LIMIT);
        List<AnomalyCauseRunDto> runOptions = new ArrayList<>();

        for (Document row : runRows) {
            String runId = normalizeOptionalText(row.get("run_id"));
            if (runId == null) {
                continue;
            }

            String datasetKey = schemaResolver.normalizeDatasetKeyString(row.get("dataset_key"));
            String algoCode = normalizeOptionalText(row.get("algo_code"));
            String algoName = normalizeOptionalText(row.get("algo_name"));
            if (algoName == null) {
                algoName = defaultAlgoName(algoCode);
            }

            String executedAt = normalizeTimestamp(row.get("executed_at"));
            String label = runId + " (" + algoName + ")";

            runOptions.add(new AnomalyCauseRunDto(
                    runId,
                    datasetKey,
                    algoCode,
                    algoName,
                    label,
                    executedAt
            ));
        }

        return runOptions;
    }

    @Override
    public AnomalyCauseListResponseDto getAnomalyCauseList(
            String runId,
            String datasetKey,
            String equipmentId,
            String status,
            String from,
            String to,
            Integer page,
            Integer size
    ) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        String normalizedDatasetKey = normalizeRequiredText(datasetKey, "datasetKey");
        String normalizedEquipmentId = normalizeRequiredText(equipmentId, "equipmentId");
        String normalizedStatus = resolveStatusFilter(status);
        Date fromDate = parseOptionalDate(from, "from");
        Date toDate = parseOptionalDate(to, "to");

        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            throw new IllegalArgumentException("from must be less than or equal to to.");
        }
        if (!anomalyCauseRepository.existsAnomalyRunIdentity(
                normalizedRunId,
                normalizedDatasetKey,
                normalizedEquipmentId
        )) {
            throw new IllegalArgumentException("runId, datasetKey, and equipmentId combination is invalid.");
        }

        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size);
        int skip = resolvedPage * resolvedSize;

        AnomalyCauseRepository.WindowQuery windowQuery = new AnomalyCauseRepository.WindowQuery(
                normalizedRunId,
                normalizedDatasetKey,
                normalizedEquipmentId,
                normalizedStatus,
                fromDate,
                toDate
        );

        long total = anomalyCauseRepository.countAnomalyWindows(windowQuery);
        List<AnomalyCauseListItemDto> items = List.of();
        if (total > 0L && skip < total) {
            List<Document> rows = anomalyCauseRepository.findAnomalyWindows(windowQuery, skip, resolvedSize);
            items = rows.stream()
                    .map(this::toListItem)
                    .toList();
        }

        int totalPages = total == 0L ? 0 : (int) Math.ceil(total / (double) resolvedSize);
        return new AnomalyCauseListResponseDto(
                items,
                total,
                resolvedPage,
                resolvedSize,
                totalPages
        );
    }

    @Override
    public AnomalyCauseDetailDto getAnomalyCauseDetail(
            String runId,
            String datasetKey,
            String windowStart,
            String windowEnd,
            String equipmentId
    ) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        String normalizedDatasetKey = normalizeRequiredText(datasetKey, "datasetKey");
        String normalizedEquipmentId = normalizeRequiredText(equipmentId, "equipmentId");
        Date windowStartDate = parseRequiredDate(windowStart, "windowStart");
        Date windowEndDate = parseRequiredDate(windowEnd, "windowEnd");
        if (windowStartDate.after(windowEndDate)) {
            throw new IllegalArgumentException("windowStart must be less than or equal to windowEnd.");
        }

        Document resultWindow = anomalyCauseRepository.findAnomalyResultWindow(
                normalizedRunId,
                normalizedDatasetKey,
                windowStartDate,
                windowEndDate,
                normalizedEquipmentId
        );
        Document causeWindow = anomalyCauseRepository.findAnomalyCauseWindow(
                normalizedRunId,
                normalizedDatasetKey,
                windowStartDate,
                windowEndDate,
                normalizedEquipmentId
        );

        if (resultWindow == null && causeWindow == null) {
            throw new IllegalArgumentException("Selected window not found.");
        }

        List<Document> causeCandidateDocs = causeWindow == null
                ? List.of()
                : asDocumentList(causeWindow.get("cause_candidates"));

        List<String> sourceFields = collectSourceFields(causeCandidateDocs);
        Map<String, FeatureCauseMeta> causeMetaBySourceField = selectFeatureCauseMeta(
                anomalyCauseRepository.findFeatureCauseMaps(normalizedDatasetKey, sourceFields),
                normalizedDatasetKey
        );

        List<GroupScoreDto> groupScores = toGroupScores(causeWindow == null ? null : causeWindow.get("group_scores"));
        List<String> causeSummary = normalizeCauseSummary(causeWindow == null ? null : causeWindow.get("cause_summary"), groupScores);
        List<CauseCandidateDto> causeCandidates = toCauseCandidates(causeCandidateDocs, causeMetaBySourceField);

        String resolvedWindowStart = normalizeTimestamp(
                causeWindow == null ? (resultWindow == null ? windowStartDate : resultWindow.get("window_start")) : causeWindow.get("window_start")
        );
        String resolvedWindowEnd = normalizeTimestamp(
                causeWindow == null ? (resultWindow == null ? windowEndDate : resultWindow.get("window_end")) : causeWindow.get("window_end")
        );

        return new AnomalyCauseDetailDto(
                normalizedRunId,
                normalizedDatasetKey,
                firstNonBlank(
                        normalizeOptionalText(causeWindow == null ? null : causeWindow.get("equipment_id")),
                        normalizeOptionalText(causeWindow == null ? null : causeWindow.get("MCCODE")),
                        normalizeOptionalText(resultWindow == null ? null : resultWindow.get("MCCODE")),
                        normalizeOptionalText(resultWindow == null ? null : resultWindow.get("equipment_id"))
                ),
                resolvedWindowStart,
                resolvedWindowEnd,
                firstNonNullDouble(
                        asDouble(causeWindow == null ? null : causeWindow.get("anomaly_score")),
                        asDouble(resultWindow == null ? null : resultWindow.get("anomaly_score"))
                ),
                firstNonNullDouble(
                        asDouble(causeWindow == null ? null : causeWindow.get("health_index")),
                        asDouble(resultWindow == null ? null : resultWindow.get("health_index"))
                ),
                firstNonBlank(
                        normalizeOptionalText(causeWindow == null ? null : causeWindow.get("anomaly_status")),
                        normalizeOptionalText(resultWindow == null ? null : resultWindow.get("status"))
                ),
                normalizeOptionalText(causeWindow == null ? null : causeWindow.get("cause_method")),
                normalizeOptionalText(causeWindow == null ? null : causeWindow.get("cause_version")),
                toBaselineScope(causeWindow == null ? null : causeWindow.get("baseline_scope")),
                causeSummary,
                causeCandidates,
                groupScores,
                normalizeSourceRef(causeWindow == null ? null : causeWindow.get("source_ref")),
                causeWindow != null,
                normalizeTimestamp(causeWindow == null ? null : causeWindow.get("created_at")),
                normalizeTimestamp(causeWindow == null ? null : causeWindow.get("updated_at"))
        );
    }

    @Override
    public AnomalyCauseDetailDto recalculateAnomalyCause(
            String runId,
            String datasetKey,
            String equipmentId,
            String windowStart,
            String windowEnd
    ) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        String normalizedDatasetKey = normalizeRequiredText(datasetKey, "datasetKey");
        String normalizedEquipmentId = normalizeRequiredText(equipmentId, "equipmentId");
        Date windowStartDate = parseRequiredDate(windowStart, "windowStart");
        Date windowEndDate = parseRequiredDate(windowEnd, "windowEnd");
        if (windowStartDate.after(windowEndDate)) {
            throw new IllegalArgumentException("windowStart must be less than or equal to windowEnd.");
        }

        Document targetWindow = anomalyCauseRepository.findAnomalyResultWindow(
                normalizedRunId,
                normalizedDatasetKey,
                windowStartDate,
                windowEndDate,
                normalizedEquipmentId
        );
        if (targetWindow == null) {
            throw new IllegalArgumentException("Selected window not found.");
        }

        Map<String, Map<String, Double>> featureFallbackCache = new LinkedHashMap<>();
        Map<String, Double> currentFeatures = resolveCurrentFeatures(targetWindow, normalizedDatasetKey, featureFallbackCache);
        List<ParsedFeatureKey> parsedFeatureKeys = collectParsedFeatureKeys(currentFeatures);
        List<String> sourceFields = parsedFeatureKeys.stream()
                .map(ParsedFeatureKey::sourceField)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, FeatureCauseMeta> causeMetaBySourceField = selectFeatureCauseMeta(
                anomalyCauseRepository.findFeatureCauseMaps(normalizedDatasetKey, sourceFields),
                normalizedDatasetKey
        );
        BaselineBundle baseline = buildBaseline(
                normalizedDatasetKey,
                normalizedRunId,
                normalizedEquipmentId,
                featureFallbackCache
        );

        recalculateAndUpsertWindow(targetWindow, normalizedDatasetKey, currentFeatures, causeMetaBySourceField, baseline);
        return getAnomalyCauseDetail(
                normalizedRunId,
                normalizedDatasetKey,
                normalizeTimestamp(windowStartDate),
                normalizeTimestamp(windowEndDate),
                normalizedEquipmentId
        );
    }

    @Override
    public AnomalyCauseRecalculateRunResultDto recalculateRun(
            String runId,
            String datasetKey,
            String equipmentId
    ) {
        String normalizedRunId = normalizeRequiredText(runId, "runId");
        String normalizedDatasetKey = normalizeRequiredText(datasetKey, "datasetKey");
        String normalizedEquipmentId = normalizeRequiredText(equipmentId, "equipmentId");

        List<Document> targetWindows = anomalyCauseRepository.findAnomalyResultsByRunAndEquipment(
                normalizedRunId,
                normalizedDatasetKey,
                normalizedEquipmentId
        );
        if (targetWindows.isEmpty()) {
            return new AnomalyCauseRecalculateRunResultDto(
                    normalizedRunId,
                    normalizedDatasetKey,
                    normalizedEquipmentId,
                    0,
                    0,
                    0
            );
        }

        Map<String, Map<String, Double>> featureFallbackCache = new LinkedHashMap<>();
        Map<String, Map<String, Double>> currentFeaturesByWindow = new LinkedHashMap<>();
        LinkedHashSet<String> sourceFieldSet = new LinkedHashSet<>();

        for (Document targetWindow : targetWindows) {
            Date windowStartDate = asDate(targetWindow.get("window_start"));
            Date windowEndDate = asDate(targetWindow.get("window_end"));
            if (windowStartDate == null || windowEndDate == null) {
                continue;
            }
            String windowKey = buildWindowKey(windowStartDate, windowEndDate);
            Map<String, Double> currentFeatures = resolveCurrentFeatures(targetWindow, normalizedDatasetKey, featureFallbackCache);
            currentFeaturesByWindow.put(windowKey, currentFeatures);
            for (ParsedFeatureKey parsedFeatureKey : collectParsedFeatureKeys(currentFeatures)) {
                sourceFieldSet.add(parsedFeatureKey.sourceField());
            }
        }

        Map<String, FeatureCauseMeta> causeMetaBySourceField = selectFeatureCauseMeta(
                anomalyCauseRepository.findFeatureCauseMaps(normalizedDatasetKey, List.copyOf(sourceFieldSet)),
                normalizedDatasetKey
        );
        BaselineBundle baseline = buildBaseline(
                normalizedDatasetKey,
                normalizedRunId,
                normalizedEquipmentId,
                featureFallbackCache
        );

        int processedCount = 0;
        int createdOrUpdatedCount = 0;
        int skippedCount = 0;

        for (Document targetWindow : targetWindows) {
            processedCount++;
            Date windowStartDate = asDate(targetWindow.get("window_start"));
            Date windowEndDate = asDate(targetWindow.get("window_end"));
            if (windowStartDate == null || windowEndDate == null) {
                skippedCount++;
                continue;
            }

            String windowKey = buildWindowKey(windowStartDate, windowEndDate);
            Map<String, Double> currentFeatures = currentFeaturesByWindow.getOrDefault(windowKey, Map.of());
            try {
                Document updated = recalculateAndUpsertWindow(
                        targetWindow,
                        normalizedDatasetKey,
                        currentFeatures,
                        causeMetaBySourceField,
                        baseline
                );
                if (updated == null) {
                    skippedCount++;
                    continue;
                }
                createdOrUpdatedCount++;
            } catch (RuntimeException exception) {
                log.warn(
                        "Anomaly cause recalculation skipped due to error. runId={}, datasetKey={}, equipmentId={}, windowStart={}, windowEnd={}",
                        normalizedRunId,
                        normalizedDatasetKey,
                        normalizedEquipmentId,
                        normalizeTimestamp(windowStartDate),
                        normalizeTimestamp(windowEndDate),
                        exception
                );
                skippedCount++;
            }
        }

        return new AnomalyCauseRecalculateRunResultDto(
                normalizedRunId,
                normalizedDatasetKey,
                normalizedEquipmentId,
                processedCount,
                createdOrUpdatedCount,
                skippedCount
        );
    }

    @Override
    public AnomalyCauseBackfillResultDto backfillMissingCauses(Integer limit) {
        int resolvedLimit = resolveBackfillLimit(limit);
        List<Document> targets = anomalyCauseRepository.findMissingCauseTargets(resolvedLimit);

        if (targets.isEmpty()) {
            return new AnomalyCauseBackfillResultDto(
                    resolvedLimit,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        Map<RunDatasetKey, List<Document>> groupedTargets = new LinkedHashMap<>();
        int skippedCount = 0;
        for (Document target : targets) {
            String runId = normalizeOptionalText(target.get("run_id"));
            String datasetKey = normalizeOptionalText(target.get("dataset_key"));
            String equipmentId = firstNonBlank(
                    normalizeOptionalText(target.get("equipment_id")),
                    normalizeOptionalText(target.get("MCCODE"))
            );
            if (runId == null || datasetKey == null || equipmentId == null) {
                skippedCount++;
                continue;
            }

            RunDatasetKey key = new RunDatasetKey(runId, datasetKey, equipmentId);
            groupedTargets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(target);
        }

        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;

        for (Map.Entry<RunDatasetKey, List<Document>> entry : groupedTargets.entrySet()) {
            RunDatasetKey key = entry.getKey();
            List<Document> runTargets = entry.getValue();

            Map<String, Map<String, Double>> featureFallbackCache = new LinkedHashMap<>();
            Map<String, Map<String, Double>> currentFeaturesByWindow = new LinkedHashMap<>();
            LinkedHashSet<String> sourceFieldSet = new LinkedHashSet<>();

            try {
                for (Document targetWindow : runTargets) {
                    Date windowStartDate = asDate(targetWindow.get("window_start"));
                    Date windowEndDate = asDate(targetWindow.get("window_end"));
                    if (windowStartDate == null || windowEndDate == null) {
                        continue;
                    }

                    String windowKey = buildWindowKey(windowStartDate, windowEndDate);
                    Map<String, Double> currentFeatures = resolveCurrentFeatures(
                            targetWindow,
                            key.datasetKey(),
                            featureFallbackCache
                    );
                    currentFeaturesByWindow.put(windowKey, currentFeatures);
                    for (ParsedFeatureKey parsedFeatureKey : collectParsedFeatureKeys(currentFeatures)) {
                        sourceFieldSet.add(parsedFeatureKey.sourceField());
                    }
                }

                Map<String, FeatureCauseMeta> causeMetaBySourceField = selectFeatureCauseMeta(
                        anomalyCauseRepository.findFeatureCauseMaps(key.datasetKey(), List.copyOf(sourceFieldSet)),
                        key.datasetKey()
                );
                BaselineBundle baseline = buildBaseline(
                        key.datasetKey(),
                        key.runId(),
                        key.equipmentId(),
                        featureFallbackCache
                );

                for (Document targetWindow : runTargets) {
                    processedCount++;

                    Date windowStartDate = asDate(targetWindow.get("window_start"));
                    Date windowEndDate = asDate(targetWindow.get("window_end"));
                    if (windowStartDate == null || windowEndDate == null) {
                        skippedCount++;
                        continue;
                    }

                    String windowKey = buildWindowKey(windowStartDate, windowEndDate);
                    Map<String, Double> currentFeatures = currentFeaturesByWindow.getOrDefault(windowKey, Map.of());
                    try {
                        Document updated = recalculateAndUpsertWindow(
                                targetWindow,
                                key.datasetKey(),
                                currentFeatures,
                                causeMetaBySourceField,
                                baseline
                        );
                        if (updated == null) {
                            skippedCount++;
                            continue;
                        }
                        successCount++;
                    } catch (RuntimeException exception) {
                        failureCount++;
                        log.warn(
                                "Anomaly cause backfill failed for window. runId={}, datasetKey={}, windowStart={}, windowEnd={}",
                                key.runId(),
                                key.datasetKey(),
                                normalizeTimestamp(windowStartDate),
                                normalizeTimestamp(windowEndDate),
                                exception
                        );
                    }
                }
            } catch (RuntimeException exception) {
                processedCount += runTargets.size();
                failureCount += runTargets.size();
                log.warn(
                        "Anomaly cause backfill context build failed. runId={}, datasetKey={}, targetCount={}",
                        key.runId(),
                        key.datasetKey(),
                        runTargets.size(),
                        exception
                );
            }
        }

        return new AnomalyCauseBackfillResultDto(
                resolvedLimit,
                targets.size(),
                processedCount,
                successCount,
                failureCount,
                skippedCount
        );
    }

    private Document recalculateAndUpsertWindow(
            Document targetWindow,
            String datasetKey,
            Map<String, Double> currentFeatures,
            Map<String, FeatureCauseMeta> causeMetaBySourceField,
            BaselineBundle baseline
    ) {
        Date windowStartDate = asDate(targetWindow.get("window_start"));
        Date windowEndDate = asDate(targetWindow.get("window_end"));
        if (windowStartDate == null || windowEndDate == null) {
            return null;
        }

        List<Document> causeCandidates = buildCauseCandidates(currentFeatures, baseline, causeMetaBySourceField);
        List<Document> topCandidates = causeCandidates.size() > DEFAULT_TOP_N
                ? new ArrayList<>(causeCandidates.subList(0, DEFAULT_TOP_N))
                : causeCandidates;
        List<Document> groupScores = buildGroupScores(topCandidates);
        List<String> causeSummary = buildCauseSummary(groupScores);

        Date now = new Date();
        Document baselineScope = new Document("dataset_key", datasetKey)
                .append("status_filter", STATUS_NORMAL)
                .append("sample_count", baseline.sampleCount())
                .append("scope_type", baseline.scopeType());
        if (baseline.from() != null) {
            baselineScope.put("from", baseline.from());
        }
        if (baseline.to() != null) {
            baselineScope.put("to", baseline.to());
        }

        Document sourceRef = new Document("anomaly_result_collection", "thisanomalyresult")
                .append("feature_collection", "thisfeature")
                .append("feature_cause_map_collection", "tmst_feature_cause_map")
                .append("feature_source", "input_features_first")
                .append("baseline_scope_type", baseline.scopeType())
                .append("baseline_min_samples", MIN_BASELINE_SAMPLES);

        String resolvedEquipmentId = firstNonBlank(
                normalizeOptionalText(targetWindow.get("equipment_id")),
                normalizeOptionalText(targetWindow.get("MCCODE"))
        );
        Document causeDocument = new Document("run_id", normalizeOptionalText(targetWindow.get("run_id")))
                .append("dataset_key", datasetKey)
                .append("equipment_id", resolvedEquipmentId)
                .append("MCCODE", resolvedEquipmentId)
                .append("anomaly_result_id", normalizeOptionalText(targetWindow.get("_id")))
                .append("window_start", windowStartDate)
                .append("window_end", windowEndDate)
                .append("anomaly_score", asDouble(targetWindow.get("anomaly_score")))
                .append("health_index", asDouble(targetWindow.get("health_index")))
                .append("anomaly_status", normalizeOptionalText(targetWindow.get("status")))
                .append("cause_method", CAUSE_METHOD)
                .append("cause_version", CAUSE_VERSION)
                .append("baseline_scope", baselineScope)
                .append("cause_summary", causeSummary)
                .append("cause_candidates", topCandidates)
                .append("group_scores", groupScores)
                .append("source_ref", sourceRef)
                .append("updated_at", now)
                .append("created_at", now);

        return anomalyCauseRepository.upsertAnomalyCause(causeDocument);
    }

    private BaselineBundle buildBaseline(
            String datasetKey,
            String runId,
            String equipmentId,
            Map<String, Map<String, Double>> featureFallbackCache
    ) {
        List<Document> sameRunNormalRows = anomalyCauseRepository.findNormalAnomalyResultsByRunAndDataset(
                runId,
                datasetKey,
                equipmentId
        );
        if (sameRunNormalRows.size() >= MIN_BASELINE_SAMPLES) {
            return createBaselineBundle(
                    BASELINE_SCOPE_SAME_RUN,
                    sameRunNormalRows,
                    datasetKey,
                    featureFallbackCache
            );
        }

        List<Document> datasetNormalRows = anomalyCauseRepository.findNormalAnomalyResultsByDataset(
                datasetKey,
                equipmentId
        );
        if (datasetNormalRows.size() >= MIN_BASELINE_SAMPLES) {
            return createBaselineBundle(
                    BASELINE_SCOPE_DATASET,
                    datasetNormalRows,
                    datasetKey,
                    featureFallbackCache
            );
        }

        List<Document> insufficientRows = datasetNormalRows.isEmpty() ? sameRunNormalRows : datasetNormalRows;
        Date from = null;
        Date to = null;
        for (Document row : insufficientRows) {
            Date windowStart = asDate(row.get("window_start"));
            Date windowEnd = asDate(row.get("window_end"));
            if (windowStart != null && (from == null || windowStart.before(from))) {
                from = windowStart;
            }
            if (windowEnd != null && (to == null || windowEnd.after(to))) {
                to = windowEnd;
            }
        }

        return new BaselineBundle(
                BASELINE_SCOPE_INSUFFICIENT,
                Map.of(),
                from,
                to,
                insufficientRows.size(),
                false
        );
    }

    private BaselineBundle createBaselineBundle(
            String scopeType,
            List<Document> baselineRows,
            String datasetKey,
            Map<String, Map<String, Double>> featureFallbackCache
    ) {
        Date from = null;
        Date to = null;
        Map<String, NumericAccumulator> accumulators = new LinkedHashMap<>();

        for (Document baselineRow : baselineRows) {
            Date windowStartDate = asDate(baselineRow.get("window_start"));
            Date windowEndDate = asDate(baselineRow.get("window_end"));
            if (windowStartDate != null && (from == null || windowStartDate.before(from))) {
                from = windowStartDate;
            }
            if (windowEndDate != null && (to == null || windowEndDate.after(to))) {
                to = windowEndDate;
            }

            Map<String, Double> features = resolveCurrentFeatures(baselineRow, datasetKey, featureFallbackCache);
            for (Map.Entry<String, Double> feature : features.entrySet()) {
                Double featureValue = feature.getValue();
                if (!isFinite(featureValue)) {
                    continue;
                }
                String featureKey = normalizeOptionalText(feature.getKey());
                if (featureKey == null) {
                    continue;
                }
                String normalizedFeatureKey = featureKey.toLowerCase(Locale.ROOT);
                NumericAccumulator accumulator = accumulators.computeIfAbsent(
                        normalizedFeatureKey,
                        ignored -> new NumericAccumulator()
                );
                accumulator.add(featureValue);
            }
        }

        Map<String, FeatureStats> baselineFeatureStats = new LinkedHashMap<>();
        for (Map.Entry<String, NumericAccumulator> entry : accumulators.entrySet()) {
            NumericAccumulator accumulator = entry.getValue();
            if (accumulator.count < MIN_BASELINE_SAMPLES) {
                continue;
            }
            double mean = accumulator.sum / accumulator.count;
            Double std = null;
            if (accumulator.count >= 2) {
                double variance = (accumulator.sumSquares - ((accumulator.sum * accumulator.sum) / accumulator.count))
                        / (accumulator.count - 1);
                if (variance < 0D && variance > -1e-12D) {
                    variance = 0D;
                }
                if (Double.isFinite(variance) && variance >= 0D) {
                    double computedStd = Math.sqrt(variance);
                    if (Double.isFinite(computedStd)) {
                        std = computedStd;
                    }
                }
            }
            baselineFeatureStats.put(entry.getKey(), new FeatureStats(accumulator.count, mean, std));
        }

        return new BaselineBundle(
                scopeType,
                Map.copyOf(baselineFeatureStats),
                from,
                to,
                baselineRows.size(),
                baselineRows.size() >= MIN_BASELINE_SAMPLES
        );
    }

    private Map<String, Double> resolveCurrentFeatures(
            Document anomalyResult,
            String datasetKey,
            Map<String, Map<String, Double>> featureFallbackCache
    ) {
        Map<String, Double> fromInputFeatures = flattenInputFeatureMap(anomalyResult.get("input_features"));
        if (!fromInputFeatures.isEmpty()) {
            return fromInputFeatures;
        }

        Date windowStartDate = asDate(anomalyResult.get("window_start"));
        Date windowEndDate = asDate(anomalyResult.get("window_end"));
        if (windowStartDate == null || windowEndDate == null) {
            return Map.of();
        }

        String windowKey = buildWindowKey(windowStartDate, windowEndDate);
        if (featureFallbackCache.containsKey(windowKey)) {
            return featureFallbackCache.get(windowKey);
        }

        Document featureWindow = anomalyCauseRepository.findFeatureWindow(datasetKey, windowStartDate, windowEndDate);
        Map<String, Double> flattenedFeatureValues = flattenFeatureValues(featureWindow == null ? null : featureWindow.get("feature_values"));
        featureFallbackCache.put(windowKey, flattenedFeatureValues);
        return flattenedFeatureValues;
    }

    private Map<String, Double> flattenInputFeatureMap(Object rawInputFeatures) {
        if (!(rawInputFeatures instanceof Map<?, ?> inputFeaturesMap)) {
            return Map.of();
        }

        LinkedHashMap<String, Double> flattened = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : inputFeaturesMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = normalizeOptionalText(entry.getKey());
            if (key == null) {
                continue;
            }
            Double value = asDouble(entry.getValue());
            if (!isFinite(value)) {
                continue;
            }
            flattened.put(key, value);
        }
        return Map.copyOf(flattened);
    }

    private Map<String, Double> flattenFeatureValues(Object rawFeatureValues) {
        if (!(rawFeatureValues instanceof Map<?, ?> featureValuesMap)) {
            return Map.of();
        }

        LinkedHashMap<String, Double> flattened = new LinkedHashMap<>();
        for (String stat : SUPPORTED_STATS) {
            String sourceKey = stat.toUpperCase(Locale.ROOT);
            Object statObject = featureValuesMap.get(sourceKey);
            if (!(statObject instanceof Map<?, ?> statMap)) {
                continue;
            }
            for (Map.Entry<?, ?> entry : statMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String sourceField = normalizeOptionalText(entry.getKey());
                if (sourceField == null) {
                    continue;
                }
                Double value = asDouble(entry.getValue());
                if (!isFinite(value)) {
                    continue;
                }
                flattened.put(sourceField + "_" + stat, value);
            }
        }
        return Map.copyOf(flattened);
    }

    private List<ParsedFeatureKey> collectParsedFeatureKeys(Map<String, Double> currentFeatures) {
        if (currentFeatures == null || currentFeatures.isEmpty()) {
            return List.of();
        }

        List<ParsedFeatureKey> parsed = new ArrayList<>();
        for (String featureKey : currentFeatures.keySet()) {
            ParsedFeatureKey parsedFeatureKey = parseFeatureKey(featureKey);
            if (parsedFeatureKey == null) {
                continue;
            }
            if (isExcludedSourceField(parsedFeatureKey.sourceField())) {
                continue;
            }
            parsed.add(parsedFeatureKey);
        }
        return parsed;
    }

    private ParsedFeatureKey parseFeatureKey(String featureKey) {
        String normalizedFeatureKey = normalizeOptionalText(featureKey);
        if (normalizedFeatureKey == null) {
            return null;
        }

        String lowerFeatureKey = normalizedFeatureKey.toLowerCase(Locale.ROOT);
        for (String stat : SUPPORTED_STATS) {
            String suffix = "_" + stat;
            if (!lowerFeatureKey.endsWith(suffix)) {
                continue;
            }
            String sourceField = normalizedFeatureKey.substring(0, normalizedFeatureKey.length() - suffix.length()).trim();
            if (sourceField.isEmpty()) {
                return null;
            }
            return new ParsedFeatureKey(normalizedFeatureKey, sourceField, stat);
        }
        return null;
    }

    private boolean isExcludedSourceField(String sourceField) {
        String normalized = normalizeOptionalText(sourceField);
        if (normalized == null) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (EXCLUDED_SOURCE_FIELDS.contains(lower)) {
            return true;
        }
        return lower.endsWith("_id");
    }

    private List<Document> buildCauseCandidates(
            Map<String, Double> currentFeatures,
            BaselineBundle baseline,
            Map<String, FeatureCauseMeta> causeMetaBySourceField
    ) {
        if (currentFeatures == null || currentFeatures.isEmpty() || causeMetaBySourceField.isEmpty()) {
            return List.of();
        }

        List<Document> candidates = new ArrayList<>();
        for (Map.Entry<String, Double> feature : currentFeatures.entrySet()) {
            String featureKey = normalizeOptionalText(feature.getKey());
            Double currentValue = feature.getValue();
            if (featureKey == null || !isFinite(currentValue)) {
                continue;
            }

            ParsedFeatureKey parsedFeatureKey = parseFeatureKey(featureKey);
            if (parsedFeatureKey == null || isExcludedSourceField(parsedFeatureKey.sourceField())) {
                continue;
            }

            String sourceFieldKey = parsedFeatureKey.sourceField().toLowerCase(Locale.ROOT);
            FeatureCauseMeta causeMeta = causeMetaBySourceField.get(sourceFieldKey);
            if (causeMeta == null) {
                continue;
            }

            FeatureStats baselineFeatureStats = baseline.featureStats().get(parsedFeatureKey.featureKey().toLowerCase(Locale.ROOT));
            Double baselineMean = baselineFeatureStats == null ? null : baselineFeatureStats.mean();
            Double baselineStd = baselineFeatureStats == null ? null : baselineFeatureStats.std();

            String direction = DIRECTION_UNKNOWN;
            double deviationScore = 0D;
            if (baselineMean != null && Double.isFinite(baselineMean)) {
                double delta = currentValue - baselineMean;
                if (baselineStd != null && Double.isFinite(baselineStd) && baselineStd > BASELINE_STD_EPSILON) {
                    deviationScore = Math.abs(delta) / baselineStd;
                    if (Math.abs(delta) <= BASELINE_STD_EPSILON) {
                        direction = DIRECTION_FLAT;
                    } else if (delta > 0D) {
                        direction = DIRECTION_HIGH;
                    } else {
                        direction = DIRECTION_LOW;
                    }
                } else {
                    direction = Math.abs(delta) <= BASELINE_STD_EPSILON ? DIRECTION_FLAT : DIRECTION_UNKNOWN;
                }
            }

            String displayName = firstNonBlank(causeMeta.displayName(), parsedFeatureKey.sourceField(), parsedFeatureKey.featureKey());
            String causeGroup = firstNonBlank(causeMeta.causeGroup(), "OTHER");
            String reasonText = buildReasonText(direction, baselineMean, baselineStd, baselineFeatureStats);

            Document candidate = new Document("feature", parsedFeatureKey.featureKey())
                    .append("source_field", parsedFeatureKey.sourceField())
                    .append("stat", parsedFeatureKey.stat())
                    .append("display_name", displayName)
                    .append("cause_group", causeGroup)
                    .append("unit", causeMeta.unit())
                    .append("current_value", currentValue)
                    .append("baseline_mean", baselineMean)
                    .append("baseline_std", baselineStd)
                    .append("deviation_score", deviationScore)
                    .append("direction", direction)
                    .append("reason_text", reasonText);
            candidates.add(candidate);
        }

        candidates.sort(
                Comparator.<Document>comparingDouble(candidate -> {
                            Double score = asDouble(candidate.get("deviation_score"));
                            return score == null ? 0D : score;
                        })
                        .reversed()
                        .thenComparing(candidate -> normalizeOptionalText(candidate.get("source_field")), Comparator.nullsLast(String::compareTo))
                        .thenComparing(candidate -> normalizeOptionalText(candidate.get("feature")), Comparator.nullsLast(String::compareTo))
        );

        for (int index = 0; index < candidates.size(); index++) {
            candidates.get(index).put("rank", index + 1);
        }
        return candidates;
    }

    private String buildReasonText(
            String direction,
            Double baselineMean,
            Double baselineStd,
            FeatureStats baselineFeatureStats
    ) {
        if (baselineFeatureStats == null || baselineFeatureStats.sampleCount() < MIN_BASELINE_SAMPLES) {
            return "Normal baseline sample is insufficient.";
        }
        if (baselineMean == null || !Double.isFinite(baselineMean)) {
            return "Baseline mean is unavailable.";
        }
        if (baselineStd == null || !Double.isFinite(baselineStd) || baselineStd <= BASELINE_STD_EPSILON) {
            return "Baseline std is too small for stable z-score.";
        }
        return switch (direction) {
            case DIRECTION_HIGH -> "Higher than normal baseline mean.";
            case DIRECTION_LOW -> "Lower than normal baseline mean.";
            case DIRECTION_FLAT -> "Near normal baseline mean.";
            default -> "Direction is unavailable.";
        };
    }

    private List<Document> buildGroupScores(List<Document> causeCandidates) {
        if (causeCandidates == null || causeCandidates.isEmpty()) {
            return List.of();
        }

        Map<String, Document> groupBest = new LinkedHashMap<>();
        for (Document causeCandidate : causeCandidates) {
            String causeGroup = firstNonBlank(normalizeOptionalText(causeCandidate.get("cause_group")), "OTHER");
            Double score = asDouble(causeCandidate.get("deviation_score"));
            if (score == null) {
                score = 0D;
            }

            Document currentBest = groupBest.get(causeGroup);
            Double currentBestScore = currentBest == null ? null : asDouble(currentBest.get("score"));
            if (currentBest == null || currentBestScore == null || score > currentBestScore) {
                Document groupScore = new Document("cause_group", causeGroup)
                        .append("score", score)
                        .append("top_feature", firstNonBlank(
                                normalizeOptionalText(causeCandidate.get("display_name")),
                                normalizeOptionalText(causeCandidate.get("source_field")),
                                normalizeOptionalText(causeCandidate.get("feature"))
                        ));
                groupBest.put(causeGroup, groupScore);
            }
        }

        List<Document> sorted = new ArrayList<>(groupBest.values());
        sorted.sort(
                Comparator.<Document>comparingDouble(groupScore -> {
                            Double score = asDouble(groupScore.get("score"));
                            return score == null ? 0D : score;
                        })
                        .reversed()
                        .thenComparing(groupScore -> normalizeOptionalText(groupScore.get("cause_group")), Comparator.nullsLast(String::compareTo))
        );

        for (int index = 0; index < sorted.size(); index++) {
            sorted.get(index).put("rank", index + 1);
        }
        return sorted;
    }

    private List<String> buildCauseSummary(List<Document> groupScores) {
        if (groupScores == null || groupScores.isEmpty()) {
            return List.of();
        }

        List<String> summary = new ArrayList<>();
        for (Document groupScore : groupScores) {
            String causeGroup = normalizeOptionalText(groupScore.get("cause_group"));
            if (causeGroup == null) {
                continue;
            }
            summary.add(causeGroup);
            if (summary.size() >= SUMMARY_TOP_N) {
                break;
            }
        }
        return List.copyOf(summary);
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
        if (value instanceof String textValue) {
            try {
                return parseDate(textValue, "date");
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String buildWindowKey(Date windowStart, Date windowEnd) {
        return windowStart.toInstant() + "|" + windowEnd.toInstant();
    }

    private boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private AnomalyCauseListItemDto toListItem(Document row) {
        String runId = normalizeOptionalText(row.get("run_id"));
        String datasetKey = normalizeOptionalText(row.get("dataset_key"));
        String equipmentId = firstNonBlank(
                normalizeOptionalText(row.get("equipment_id")),
                normalizeOptionalText(row.get("MCCODE"))
        );
        String windowStart = normalizeTimestamp(row.get("window_start"));
        String windowEnd = normalizeTimestamp(row.get("window_end"));
        List<GroupScoreDto> groupScores = toGroupScores(row.get("group_scores"));
        List<String> causeSummary = normalizeCauseSummary(row.get("cause_summary"), groupScores);
        List<Document> causeCandidates = asDocumentList(row.get("cause_candidates"));

        return new AnomalyCauseListItemDto(
                buildWindowId(runId, datasetKey, equipmentId, windowStart, windowEnd),
                runId,
                datasetKey,
                equipmentId,
                windowStart,
                windowEnd,
                asDouble(row.get("anomaly_score")),
                asDouble(row.get("health_index")),
                normalizeOptionalText(row.get("status")),
                asBoolean(row.get("cause_generated"), false),
                causeSummary,
                resolveTopCauseGroup(causeSummary, groupScores),
                resolveTopDeviationScore(causeCandidates, groupScores)
        );
    }

    private String resolveTopCauseGroup(List<String> causeSummary, List<GroupScoreDto> groupScores) {
        if (causeSummary != null && !causeSummary.isEmpty()) {
            return causeSummary.get(0);
        }
        if (groupScores != null && !groupScores.isEmpty()) {
            return groupScores.get(0).causeGroup();
        }
        return null;
    }

    private Double resolveTopDeviationScore(List<Document> causeCandidates, List<GroupScoreDto> groupScores) {
        if (causeCandidates != null && !causeCandidates.isEmpty()) {
            Integer bestRank = null;
            Double bestScore = null;
            for (int index = 0; index < causeCandidates.size(); index++) {
                Document candidate = causeCandidates.get(index);
                Integer rank = asInteger(candidate.get("rank"));
                if (rank == null) {
                    rank = index + 1;
                }
                Double score = asDouble(candidate.get("deviation_score"));
                if (score == null) {
                    continue;
                }
                if (bestRank == null || rank < bestRank) {
                    bestRank = rank;
                    bestScore = score;
                }
            }
            if (bestScore != null) {
                return bestScore;
            }
        }

        if (groupScores != null && !groupScores.isEmpty()) {
            return groupScores.get(0).score();
        }
        return null;
    }

    private List<CauseCandidateDto> toCauseCandidates(
            List<Document> causeCandidateDocs,
            Map<String, FeatureCauseMeta> causeMetaBySourceField
    ) {
        if (causeCandidateDocs == null || causeCandidateDocs.isEmpty()) {
            return List.of();
        }

        List<CandidateWithOrder> orderedCandidates = new ArrayList<>();
        for (int index = 0; index < causeCandidateDocs.size(); index++) {
            Document candidate = causeCandidateDocs.get(index);
            Integer rank = asInteger(candidate.get("rank"));
            int resolvedOrder = rank == null ? (index + 1) : rank;
            orderedCandidates.add(new CandidateWithOrder(candidate, index, resolvedOrder));
        }
        orderedCandidates.sort(Comparator
                .comparingInt(CandidateWithOrder::order)
                .thenComparingInt(CandidateWithOrder::index));

        List<CauseCandidateDto> mapped = new ArrayList<>(orderedCandidates.size());
        int fallbackRank = 1;
        for (CandidateWithOrder orderedCandidate : orderedCandidates) {
            Document candidate = orderedCandidate.document();
            String sourceField = firstNonBlank(
                    normalizeOptionalText(candidate.get("source_field")),
                    normalizeOptionalText(candidate.get("feature"))
            );
            String sourceFieldKey = sourceField == null ? null : sourceField.toLowerCase(Locale.ROOT);
            FeatureCauseMeta causeMeta = sourceFieldKey == null ? null : causeMetaBySourceField.get(sourceFieldKey);

            String displayName = firstNonBlank(
                    normalizeOptionalText(candidate.get("display_name")),
                    causeMeta == null ? null : causeMeta.displayName(),
                    sourceField
            );
            String causeGroup = firstNonBlank(
                    normalizeOptionalText(candidate.get("cause_group")),
                    causeMeta == null ? null : causeMeta.causeGroup(),
                    "湲고?"
            );
            String unit = firstNonBlank(
                    normalizeOptionalText(candidate.get("unit")),
                    causeMeta == null ? null : causeMeta.unit()
            );

            Integer rank = asInteger(candidate.get("rank"));
            if (rank == null) {
                rank = fallbackRank;
            }
            fallbackRank++;

            mapped.add(new CauseCandidateDto(
                    rank,
                    firstNonBlank(normalizeOptionalText(candidate.get("feature")), sourceField),
                    sourceField,
                    normalizeOptionalText(candidate.get("stat")),
                    displayName == null ? "-" : displayName,
                    causeGroup,
                    unit,
                    asDouble(candidate.get("current_value")),
                    asDouble(candidate.get("baseline_mean")),
                    asDouble(candidate.get("baseline_std")),
                    asDouble(candidate.get("deviation_score")),
                    normalizeDirection(candidate.get("direction")),
                    normalizeOptionalText(candidate.get("reason_text"))
            ));
        }

        return mapped;
    }

    private List<GroupScoreDto> toGroupScores(Object rawGroupScores) {
        List<Document> groupScoreDocs = asDocumentList(rawGroupScores);
        if (groupScoreDocs.isEmpty()) {
            return List.of();
        }

        List<GroupScoreDto> scores = new ArrayList<>(groupScoreDocs.size());
        for (Document groupScoreDoc : groupScoreDocs) {
            scores.add(new GroupScoreDto(
                    firstNonBlank(normalizeOptionalText(groupScoreDoc.get("cause_group")), "湲고?"),
                    asDouble(groupScoreDoc.get("score")),
                    normalizeOptionalText(groupScoreDoc.get("top_feature")),
                    asInteger(groupScoreDoc.get("rank"))
            ));
        }

        scores.sort(Comparator
                .comparing(GroupScoreDto::rank, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(GroupScoreDto::score, Comparator.nullsLast(Comparator.reverseOrder())));

        List<GroupScoreDto> normalized = new ArrayList<>(scores.size());
        int fallbackRank = 1;
        for (GroupScoreDto score : scores) {
            normalized.add(new GroupScoreDto(
                    score.causeGroup(),
                    score.score(),
                    score.topFeature(),
                    score.rank() == null ? fallbackRank : score.rank()
            ));
            fallbackRank++;
        }
        return normalized;
    }

    private BaselineScopeDto toBaselineScope(Object rawBaselineScope) {
        if (!(rawBaselineScope instanceof Map<?, ?> baselineScopeMap)) {
            return null;
        }

        return new BaselineScopeDto(
                normalizeOptionalText(baselineScopeMap.get("dataset_key")),
                normalizeOptionalText(baselineScopeMap.get("status_filter")),
                normalizeTimestamp(baselineScopeMap.get("from")),
                normalizeTimestamp(baselineScopeMap.get("to")),
                asLong(baselineScopeMap.get("sample_count"))
        );
    }

    private Map<String, Object> normalizeSourceRef(Object rawSourceRef) {
        if (rawSourceRef == null) {
            return Map.of();
        }

        Object normalizedValue = schemaResolver.normalizeResponseValue(rawSourceRef);
        if (!(normalizedValue instanceof Map<?, ?> mapValue)) {
            return Map.of();
        }

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return Map.copyOf(normalized);
    }

    private Map<String, FeatureCauseMeta> selectFeatureCauseMeta(List<Document> rawMaps, String datasetKey) {
        if (rawMaps == null || rawMaps.isEmpty()) {
            return Map.of();
        }

        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        LinkedHashMap<String, FeatureCauseMeta> selected = new LinkedHashMap<>();
        for (Document mapRow : rawMaps) {
            String sourceField = normalizeOptionalText(mapRow.get("source_field"));
            if (sourceField == null) {
                continue;
            }

            String sourceFieldKey = sourceField.toLowerCase(Locale.ROOT);
            String mapDatasetKey = schemaResolver.normalizeDatasetKeyString(mapRow.get("dataset_key"));
            int priority = 1;
            if (normalizedDatasetKey != null && normalizedDatasetKey.equals(mapDatasetKey)) {
                priority = 2;
            }

            FeatureCauseMeta candidate = new FeatureCauseMeta(
                    normalizeOptionalText(mapRow.get("display_name")),
                    normalizeOptionalText(mapRow.get("cause_group")),
                    normalizeOptionalText(mapRow.get("unit")),
                    priority
            );

            FeatureCauseMeta current = selected.get(sourceFieldKey);
            if (current == null || candidate.priority() > current.priority()) {
                selected.put(sourceFieldKey, candidate);
            }
        }
        return Map.copyOf(selected);
    }

    private List<String> collectSourceFields(List<Document> causeCandidateDocs) {
        if (causeCandidateDocs == null || causeCandidateDocs.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> sourceFields = new LinkedHashSet<>();
        for (Document candidate : causeCandidateDocs) {
            String sourceField = firstNonBlank(
                    normalizeOptionalText(candidate.get("source_field")),
                    normalizeOptionalText(candidate.get("feature"))
            );
            if (sourceField != null) {
                sourceFields.add(sourceField);
            }
        }
        return List.copyOf(sourceFields);
    }

    private List<String> normalizeCauseSummary(Object rawCauseSummary, List<GroupScoreDto> groupScores) {
        LinkedHashSet<String> normalizedSummary = new LinkedHashSet<>();
        if (rawCauseSummary instanceof List<?> causeSummaryList) {
            for (Object causeSummaryItem : causeSummaryList) {
                String normalizedItem = null;
                if (causeSummaryItem instanceof String summaryText) {
                    normalizedItem = normalizeOptionalText(summaryText);
                } else if (causeSummaryItem instanceof Map<?, ?> summaryMap) {
                    normalizedItem = firstNonBlank(
                            normalizeOptionalText(summaryMap.get("cause_group")),
                            normalizeOptionalText(summaryMap.get("display_name")),
                            normalizeOptionalText(summaryMap.get("label"))
                    );
                }
                if (normalizedItem != null) {
                    normalizedSummary.add(normalizedItem);
                }
            }
        }

        if (normalizedSummary.isEmpty() && groupScores != null) {
            for (GroupScoreDto groupScore : groupScores) {
                String causeGroup = normalizeOptionalText(groupScore.causeGroup());
                if (causeGroup != null) {
                    normalizedSummary.add(causeGroup);
                }
            }
        }
        return List.copyOf(normalizedSummary);
    }

    private String buildWindowId(
            String runId,
            String datasetKey,
            String equipmentId,
            String windowStart,
            String windowEnd
    ) {
        return String.join(
                "|",
                normalizeOptionalText(runId) == null ? "-" : runId,
                normalizeOptionalText(datasetKey) == null ? "-" : datasetKey,
                normalizeOptionalText(equipmentId) == null ? "-" : equipmentId,
                normalizeOptionalText(windowStart) == null ? "-" : windowStart,
                normalizeOptionalText(windowEnd) == null ? "-" : windowEnd
        );
    }

    private String normalizeDirection(Object rawDirection) {
        String direction = normalizeOptionalText(rawDirection);
        if (direction == null) {
            return "UNKNOWN";
        }
        String normalized = direction.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "LOW", "FLAT", "UNKNOWN" -> normalized;
            default -> "UNKNOWN";
        };
    }

    private Date parseOptionalDate(String rawDate, String fieldName) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return null;
        }
        return parseDate(rawDate, fieldName);
    }

    private Date parseRequiredDate(String rawDate, String fieldName) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
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

    private int resolvePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0.");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be greater than 0.");
        }
        return Math.min(size, MAX_SIZE);
    }

    private int resolveBackfillLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_BACKFILL_LIMIT;
        }
        if (limit <= 0) {
            return DEFAULT_BACKFILL_LIMIT;
        }
        return Math.min(limit, MAX_SIZE);
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

    private Double firstNonNullDouble(Double first, Double second) {
        return first != null ? first : second;
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

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInteger(Object value) {
        Double numericValue = asDouble(value);
        if (numericValue == null) {
            return null;
        }
        return numericValue.intValue();
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
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
        return defaultValue;
    }

    private List<Document> asDocumentList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Document> documents = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item instanceof Document document) {
                documents.add(document);
                continue;
            }
            if (item instanceof Map<?, ?> mapValue) {
                Document document = new Document();
                for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    document.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                documents.add(document);
            }
        }
        return documents;
    }

    private static class NumericAccumulator {
        private long count;
        private double sum;
        private double sumSquares;

        private void add(double value) {
            count++;
            sum += value;
            sumSquares += value * value;
        }
    }

    private record BaselineBundle(
            String scopeType,
            Map<String, FeatureStats> featureStats,
            Date from,
            Date to,
            long sampleCount,
            boolean sufficient
    ) {
    }

    private record FeatureStats(
            long sampleCount,
            Double mean,
            Double std
    ) {
    }

    private record ParsedFeatureKey(
            String featureKey,
            String sourceField,
            String stat
    ) {
    }

    private record CandidateWithOrder(
            Document document,
            int index,
            int order
    ) {
    }

    private record FeatureCauseMeta(
            String displayName,
            String causeGroup,
            String unit,
            int priority
    ) {
    }

    private record RunDatasetKey(
            String runId,
            String datasetKey,
            String equipmentId
    ) {
    }
}
