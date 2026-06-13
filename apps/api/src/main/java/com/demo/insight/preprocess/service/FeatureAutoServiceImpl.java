package com.demo.insight.preprocess.service;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.equipment.service.EquipmentMasterService;
import com.demo.insight.preprocess.domain.DataTypeDatasetDocument;
import com.demo.insight.preprocess.dto.FeatureAutoJobListResponseDto;
import com.demo.insight.preprocess.dto.FeatureAutoJobStatusDto;
import com.demo.insight.preprocess.dto.FeatureAutoJobUpsertRequestDto;
import com.demo.insight.preprocess.dto.FeatureAutoTriggerRequestDto;
import com.demo.insight.preprocess.dto.FeatureAutoTriggerResponseDto;
import com.demo.insight.preprocess.dto.FeatureAutoTriggerResultDto;
import com.demo.insight.preprocess.repository.DataTypeDatasetRepository;
import com.demo.insight.preprocess.repository.FeatureAutoJobRepository;
import com.demo.insight.preprocess.repository.FeatureRepository;
import com.demo.insight.preprocess.repository.RawPreviewRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
import java.util.concurrent.locks.ReentrantLock;

@Service
public class FeatureAutoServiceImpl implements FeatureAutoService {

    private static final Logger log = LoggerFactory.getLogger(FeatureAutoServiceImpl.class);

    private static final String SOURCE_COLLECTION = "THISHMIDATA";
    private static final String LEGACY_GLOBAL_DATASET_KEY = "demo_hmi_all_default_v1";
    private static final String DEFAULT_DATASET_NAME = "Demo HMI Dataset";
    private static final String TIMESTAMP_FIELD = "PRDTIME";
    private static final String EQUIPMENT_FIELD = "MCCODE";
    private static final String DEFAULT_EQUIPMENT_SCOPE = "all";
    private static final String DEFAULT_POLICY_NAME = "default";
    private static final int DEFAULT_POLICY_VERSION = 1;

    private static final String RUN_STATUS_RUNNING = "RUNNING";
    private static final String RUN_STATUS_SUCCESS = "SUCCESS";
    private static final String RUN_STATUS_ERROR = "ERROR";
    private static final String SKIPPED_STATUS = "SKIPPED";
    private static final String SKIPPED_LEGACY_STATUS = "SKIPPED_LEGACY";
    private static final String INVALID_CONFIG_STATUS = "INVALID_CONFIG";
    private static final String RUN_STATUS_IDLE = "IDLE";
    private static final String TRIGGER_TYPE_SCHEDULE = "SCHEDULE";
    private static final String TRIGGER_TYPE_MANUAL = "MANUAL";
    private static final String USE_YN_ACTIVE = "Y";
    private static final String USE_YN_INACTIVE = "N";
    private static final double MIN_NON_NULL_RATIO_FOR_FEATURE = 0.20D;
    private static final List<String> FEATURE_STAT_KEYS = List.of("MEAN", "STD", "MIN", "MAX");

    private final RawPreviewRepository rawPreviewRepository;
    private final FeatureRepository featureRepository;
    private final FeatureAutoJobRepository featureAutoJobRepository;
    private final DataTypeDatasetRepository dataTypeDatasetRepository;
    private final DynamicSchemaResolver schemaResolver;
    private final EquipmentMasterService equipmentMasterService;

    private final ReentrantLock runLock = new ReentrantLock();

    @Value("${app.preprocess.feature-auto.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${app.preprocess.feature-auto.fixed-delay-ms:300000}")
    private long schedulerFixedDelayMs;

    @Value("${app.preprocess.feature-auto.max-raw-batch-size:5000}")
    private int maxRawBatchSize;

    @Value("${app.preprocess.feature-auto.default-window-size:100}")
    private int defaultWindowSize;

    @Value("${app.preprocess.feature-auto.default-target-collection:thisfeature}")
    private String defaultTargetCollection;

    public FeatureAutoServiceImpl(
            RawPreviewRepository rawPreviewRepository,
            FeatureRepository featureRepository,
            FeatureAutoJobRepository featureAutoJobRepository,
            DataTypeDatasetRepository dataTypeDatasetRepository,
            DynamicSchemaResolver schemaResolver,
            EquipmentMasterService equipmentMasterService
    ) {
        this.rawPreviewRepository = rawPreviewRepository;
        this.featureRepository = featureRepository;
        this.featureAutoJobRepository = featureAutoJobRepository;
        this.dataTypeDatasetRepository = dataTypeDatasetRepository;
        this.schemaResolver = schemaResolver;
        this.equipmentMasterService = equipmentMasterService;
    }

    @Override
    public FeatureAutoJobListResponseDto getFeatureAutoJobs() {
        List<Document> jobs = featureAutoJobRepository.findAllJobs();
        List<FeatureAutoJobStatusDto> jobStatuses = jobs.stream()
                .filter(this::isRuntimeStatusTargetJob)
                .map(this::toJobStatus)
                .filter(Objects::nonNull)
                .toList();

        return new FeatureAutoJobListResponseDto(
                schedulerEnabled,
                schedulerFixedDelayMs,
                jobStatuses
        );
    }

    @Override
    public FeatureAutoJobStatusDto upsertFeatureAutoJob(FeatureAutoJobUpsertRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String datasetKey = resolvePolicyDatasetKeyForSave(request.datasetKey(), request.equipmentId());
        if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)) {
            throw new IllegalArgumentException("Deprecated dataset_key is not allowed for feature-auto runtime: " + datasetKey);
        }
        String sourceCollection = resolveSourceCollectionForDatasetKey(datasetKey);
        String datasetName = schemaResolver.resolveDatasetName(request.datasetName(), sourceCollection);
        List<String> selectedColumns = normalizeSelectedColumns(request.selectedColumns());
        int windowSize = resolveWindowSize(request.windowSize());
        int scheduleIntervalSeconds = resolveScheduleIntervalSeconds(request.scheduleIntervalSeconds());
        String targetCollection = resolveTargetCollection(request.targetCollection());
        String useYn = resolveUseYn(request.useYn());
        Document existingJob = featureAutoJobRepository.findByJobId(datasetKey);
        Document datasetConfig = featureAutoJobRepository.findDatasetConfigByDatasetKey(datasetKey);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("dataset_name", datasetName);
        fields.put("dataset_label", schemaResolver.buildDatasetLabel(datasetKey, datasetName));
        fields.put("target_collection", targetCollection);
        fields.put("timestamp_field", TIMESTAMP_FIELD);
        fields.put("equipment_id_field", EQUIPMENT_FIELD);
        fields.put("selected_columns", selectedColumns);
        fields.put("feature_stats", FEATURE_STAT_KEYS);
        fields.put("window_mode", "fixed_count_only");
        fields.put("window_size", windowSize);
        fields.put("scheduler_enabled", USE_YN_ACTIVE.equals(useYn));
        fields.put("scheduler_interval_sec", scheduleIntervalSeconds);
        fields.put("use_yn", useYn);
        Document matchFilter = extractMatchFilter(existingJob);
        if (matchFilter == null || matchFilter.isEmpty()) {
            matchFilter = extractMatchFilter(datasetConfig);
        }
        if (matchFilter != null && !matchFilter.isEmpty()) {
            fields.put("match_filter", matchFilter);
        }

        Document job = featureAutoJobRepository.upsertJob(datasetKey, sourceCollection, fields);
        return toJobStatus(job);
    }

    @Override
    public FeatureAutoTriggerResponseDto triggerFeatureAutoJobs(FeatureAutoTriggerRequestDto request) {
        String requestedDatasetKey = request == null
                ? null
                : resolvePolicyDatasetKeyForRead(request.datasetKey(), request.equipmentId());

        List<Document> requestedJobs = resolveRequestedJobs(requestedDatasetKey);
        if (requestedJobs.isEmpty()) {
            return new FeatureAutoTriggerResponseDto(0, 0, 0, List.of());
        }

        if (!runLock.tryLock()) {
            List<FeatureAutoTriggerResultDto> busyResults = requestedJobs.stream()
                    .map(job -> new FeatureAutoTriggerResultDto(
                            asString(job.get("dataset_label"), "(unclassified dataset)"),
                            "SKIPPED_BUSY",
                            "Another feature generation run is already in progress.",
                            0,
                            0,
                            0,
                            0
                    ))
                    .toList();
            return new FeatureAutoTriggerResponseDto(
                    requestedJobs.size(),
                    0,
                    0,
                    busyResults
            );
        }

        try {
            List<JobRunOutcome> outcomes = runJobsInternal(TRIGGER_TYPE_MANUAL, requestedJobs, false);
            int successCount = (int) outcomes.stream().filter(JobRunOutcome::success).count();
            List<FeatureAutoTriggerResultDto> results = outcomes.stream()
                    .map(JobRunOutcome::result)
                    .toList();

            return new FeatureAutoTriggerResponseDto(
                    requestedJobs.size(),
                    outcomes.size(),
                    successCount,
                    results
            );
        } finally {
            runLock.unlock();
        }
    }

    @Override
    public void runScheduledFeatureAutoJobs() {
        if (!schedulerEnabled) {
            return;
        }

        if (!runLock.tryLock()) {
            log.info("Skipping scheduled feature-auto run because another run is in progress.");
            return;
        }

        try {
            List<Document> activeJobs = featureAutoJobRepository.findActiveSchedulerJobs();
            if (activeJobs.isEmpty()) {
                return;
            }

            List<JobRunOutcome> outcomes = runJobsInternal(TRIGGER_TYPE_SCHEDULE, activeJobs, true);
            long successCount = outcomes.stream().filter(JobRunOutcome::success).count();
            log.info("Scheduled feature-auto run finished. totalJobs={}, successJobs={}", outcomes.size(), successCount);
        } finally {
            runLock.unlock();
        }
    }

    private List<Document> resolveRequestedJobs(String requestedDatasetKey) {
        if (requestedDatasetKey == null || requestedDatasetKey.isBlank()) {
            return featureAutoJobRepository.findActiveSchedulerJobs().stream()
                    .filter(this::isRuntimeExecutionTargetJob)
                    .toList();
        }

        Document job = featureAutoJobRepository.findByJobId(requestedDatasetKey);
        if (job == null) {
            return List.of();
        }
        if (!isRuntimeExecutionTargetJob(job)) {
            return List.of();
        }
        return List.of(job);
    }

    private List<JobRunOutcome> runJobsInternal(
            String triggerType,
            List<Document> jobs,
            boolean applyScheduleInterval
    ) {
        List<JobRunOutcome> outcomes = new ArrayList<>();
        Instant now = Instant.now();

        for (Document job : jobs) {
            if (job == null) {
                continue;
            }

            String datasetKey = resolvePolicyDatasetKeyForRead(job.get("dataset_key"), job.get("equipment_scope"));
            if (!equipmentMasterService.isRuntimeOperationalDatasetKey(datasetKey)) {
                log.info(
                        "Skipping feature-auto run because dataset_key is not an active AI operational dataset. dataset_key={}",
                        datasetKey
                );
                continue;
            }
            if (isLegacyOrDeprecatedRuntimeDataset(datasetKey)) {
                if (equipmentMasterService.isLegacyGlobalDatasetKey(datasetKey)) {
                    log.info(
                            "Skipping feature-auto run for comparison-only dataset_key={}.",
                            datasetKey
                    );
                } else {
                    log.info(
                            "Skipping feature-auto run for deprecated dataset_key={}.",
                            datasetKey
                    );
                }
                continue;
            }

            if (applyScheduleInterval && !isScheduleDue(job, now)) {
                continue;
            }

            outcomes.add(executeSingleJob(job, triggerType));
        }
        return outcomes;
    }

    private boolean isScheduleDue(Document job, Instant now) {
        if (!asBoolean(job.get("scheduler_enabled"), true)) {
            return false;
        }

        int intervalSeconds = resolveScheduleIntervalSeconds(asInteger(job.get("scheduler_interval_sec")));
        String lastRunAt = asString(job.get("last_run_at"), null);
        if (lastRunAt == null) {
            return true;
        }

        try {
            Instant lastFinished = Instant.parse(lastRunAt);
            return !now.isBefore(lastFinished.plusSeconds(intervalSeconds));
        } catch (Exception ignored) {
            return true;
        }
    }

    private JobRunOutcome executeSingleJob(Document job, String triggerType) {
        String datasetKey = resolvePolicyDatasetKeyForRead(job.get("dataset_key"), job.get("equipment_scope"));
        if (isLegacyOrDeprecatedRuntimeDataset(datasetKey)) {
            String message = equipmentMasterService.isLegacyGlobalDatasetKey(datasetKey)
                    ? "Skipped: legacy comparison dataset is excluded from runtime execution."
                    : "Skipped: deprecated dataset is excluded from runtime execution.";
            String datasetLabel = asString(job.get("dataset_label"), schemaResolver.buildDatasetLabel(datasetKey, DEFAULT_DATASET_NAME));
            return new JobRunOutcome(
                    new FeatureAutoTriggerResultDto(
                            datasetLabel,
                            SKIPPED_LEGACY_STATUS,
                            message,
                            0,
                            0,
                            0,
                            0
                    ),
                    true
            );
        }
        String sourceCollection = resolveSourceCollectionFromJob(job, datasetKey);
        String datasetName = asString(job.get("dataset_name"), sourceCollection);
        String datasetLabel = asString(job.get("dataset_label"), schemaResolver.buildDatasetLabel(datasetKey, datasetName));

        int windowSize = resolveWindowSize(asInteger(job.get("window_size")));
        String targetCollection = resolveTargetCollection(asString(job.get("target_collection"), null));
        List<String> selectedColumns = normalizeSelectedColumns(asStringList(job.get("selected_columns")));
        String lastProcessedRowId = asString(job.get("last_checkpoint_value"), null);

        RawDatasetFilterSpec rawDatasetFilterSpec;
        Map<String, Object> rawDatasetFilter;
        try {
            rawDatasetFilterSpec = buildRawDatasetFilter(job, datasetKey);
            rawDatasetFilter = rawDatasetFilterSpec.rawDatasetFilter();

            Map<String, Object> startFields = new LinkedHashMap<>();
            startFields.put("last_status", RUN_STATUS_RUNNING);
            startFields.put("last_trigger_type", triggerType);
            featureAutoJobRepository.updateJob(datasetKey, startFields);

            log.info(
                    "Feature auto job started. datasetKey={}, sourceCollection={}, selectedColumns={}, windowSize={}, rawDatasetFilter={}, appliedMatchFilter={}",
                    datasetKey,
                    sourceCollection,
                    selectedColumns,
                    windowSize,
                    rawDatasetFilter,
                    rawDatasetFilterSpec.appliedMatchFilter()
            );

            List<Document> incrementalRows = rawPreviewRepository.findRowsByDatasetKeyAfterObjectId(
                    sourceCollection,
                    rawDatasetFilter,
                    lastProcessedRowId,
                    Math.max(maxRawBatchSize, 1)
            );
            List<String> numericColumns = resolveNumericColumns(incrementalRows, selectedColumns);
            if (numericColumns.isEmpty()) {
                Map<String, Object> skippedFields = new LinkedHashMap<>();
                skippedFields.put("last_status", RUN_STATUS_SUCCESS);
                skippedFields.put("last_run_at", Instant.now().toString());
                skippedFields.put("last_total_window_count", 0);
                skippedFields.put("last_created_count", 0);
                skippedFields.put("last_skipped_count", 0);
                skippedFields.put("last_consumed_raw_count", 0);
                skippedFields.put("last_error_message", null);
                featureAutoJobRepository.updateJob(datasetKey, skippedFields);
                String skipMessage = "No valid numeric columns remain after non-null filtering.";
                log.info(
                        "Feature auto job skipped. datasetKey={}, reason={}, selectedColumns={}, incrementalRows={}",
                        datasetKey,
                        skipMessage,
                        selectedColumns,
                        incrementalRows.size()
                );
                return new JobRunOutcome(
                        new FeatureAutoTriggerResultDto(
                                datasetLabel,
                                SKIPPED_STATUS,
                                skipMessage,
                                0,
                                0,
                                0,
                                0
                        ),
                        true
                );
            }

            int completeWindowCount = incrementalRows.size() / windowSize;
            log.info(
                    "Feature auto rows resolved. datasetKey={}, incrementalRows={}, completeWindowCount={}, numericColumns={}",
                    datasetKey,
                    incrementalRows.size(),
                    completeWindowCount,
                    numericColumns
            );
            if (completeWindowCount <= 0) {
                String finishedAt = Instant.now().toString();
                Map<String, Object> successFields = new LinkedHashMap<>();
                successFields.put("last_status", RUN_STATUS_SUCCESS);
                successFields.put("last_run_at", finishedAt);
                successFields.put("last_total_window_count", 0);
                successFields.put("last_created_count", 0);
                successFields.put("last_skipped_count", 0);
                successFields.put("last_consumed_raw_count", 0);
                featureAutoJobRepository.updateJob(datasetKey, successFields);
                log.info(
                        "Feature auto job skipped due to no complete window. datasetKey={}, sourceCollection={}, selectedColumns={}, windowSize={}, rawDatasetFilter={}, incrementalRows={}, completeWindowCount=0, createdCount=0",
                        datasetKey,
                        sourceCollection,
                        selectedColumns,
                        windowSize,
                        rawDatasetFilter,
                        incrementalRows.size()
                );

                return new JobRunOutcome(
                        new FeatureAutoTriggerResultDto(
                                datasetLabel,
                                "NO_COMPLETE_WINDOW",
                                "No complete window to process.",
                                0,
                                0,
                                0,
                                0
                        ),
                        true
                );
            }

            int consumedRawCount = completeWindowCount * windowSize;
            List<Document> consumableRows = incrementalRows.subList(0, consumedRawCount);

            FeatureWindowBuildResult buildResult = buildFeatureRows(
                    consumableRows,
                    datasetKey,
                    datasetName,
                    numericColumns,
                    windowSize,
                    targetCollection,
                    sourceCollection,
                    rawDatasetFilterSpec.matchFilter(),
                    rawDatasetFilterSpec.appliedMatchFilter()
            );

            int createdCount = featureRepository.upsertFeatureRows(buildResult.featureRows(), targetCollection);
            int skippedCount = Math.max(completeWindowCount - createdCount, 0);

            Document lastConsumedRow = consumableRows.get(consumableRows.size() - 1);
            String checkpointRowId = extractObjectId(lastConsumedRow.get("_id"));

            Map<String, Object> successFields = new LinkedHashMap<>();
            successFields.put("last_status", RUN_STATUS_SUCCESS);
            successFields.put("last_run_at", Instant.now().toString());
            successFields.put("last_total_window_count", completeWindowCount);
            successFields.put("last_created_count", createdCount);
            successFields.put("last_skipped_count", skippedCount);
            successFields.put("last_consumed_raw_count", consumedRawCount);

            if (checkpointRowId != null) {
                successFields.put("last_checkpoint_value", checkpointRowId);
            }
            if (buildResult.lastWindowEnd() != null) {
                successFields.put("last_window_end", buildResult.lastWindowEnd());
            }

            featureAutoJobRepository.updateJob(datasetKey, successFields);
            log.info(
                    "Feature auto job completed. datasetKey={}, sourceCollection={}, selectedColumns={}, windowSize={}, rawDatasetFilter={}, incrementalRows={}, completeWindowCount={}, createdCount={}",
                    datasetKey,
                    sourceCollection,
                    selectedColumns,
                    windowSize,
                    rawDatasetFilter,
                    incrementalRows.size(),
                    completeWindowCount,
                    createdCount
            );

            return new JobRunOutcome(
                    new FeatureAutoTriggerResultDto(
                            datasetLabel,
                            RUN_STATUS_SUCCESS,
                            "Feature generation completed.",
                            completeWindowCount,
                            createdCount,
                            skippedCount,
                            consumedRawCount
                    ),
                    true
            );
        } catch (Exception exception) {
            log.error("Feature auto job failed. datasetKey={}", datasetKey, exception);

            Map<String, Object> errorFields = new LinkedHashMap<>();
            errorFields.put("last_status", RUN_STATUS_ERROR);
            errorFields.put("last_run_at", Instant.now().toString());
            errorFields.put("last_error_message", exception.getMessage());
            featureAutoJobRepository.updateJob(datasetKey, errorFields);

            return new JobRunOutcome(
                    new FeatureAutoTriggerResultDto(
                            datasetLabel,
                            RUN_STATUS_ERROR,
                            exception.getMessage(),
                            0,
                            0,
                            0,
                            0
                    ),
                    false
            );
        }
    }

    private FeatureWindowBuildResult buildFeatureRows(
            List<Document> rawRows,
            String datasetKey,
            String datasetName,
            List<String> selectedColumns,
            int windowSize,
            String targetCollection,
            String sourceCollection,
            Document matchFilter,
            Document appliedMatchFilter
    ) {
        List<Document> featureRows = new ArrayList<>();
        String lastWindowEnd = null;
        Map<String, List<Document>> rowsByEquipment = partitionRawRowsByEquipment(rawRows);

        for (Map.Entry<String, List<Document>> equipmentEntry : rowsByEquipment.entrySet()) {
            List<Document> equipmentRows = equipmentEntry.getValue();
            if (equipmentRows == null || equipmentRows.isEmpty()) {
                continue;
            }

            for (int startIndex = 0; startIndex < equipmentRows.size(); startIndex += windowSize) {
                int endExclusive = Math.min(startIndex + windowSize, equipmentRows.size());
                List<Document> windowRows = equipmentRows.subList(startIndex, endExclusive);

                Document firstRow = windowRows.get(0);
                Document lastRow = windowRows.get(windowRows.size() - 1);
                Object windowStart = firstRow.get(TIMESTAMP_FIELD);
                Object windowEnd = lastRow.get(TIMESTAMP_FIELD);

                Instant normalizedWindowStart = normalizeTimestampAsInstant(windowStart);
                Instant normalizedWindowEnd = normalizeTimestampAsInstant(windowEnd);

                if (normalizedWindowStart == null || normalizedWindowEnd == null) {
                    log.warn(
                            "Skipping feature window because timestamp is invalid. datasetKey={}, equipmentId={}, windowStart={}, windowEnd={}",
                            datasetKey,
                            equipmentEntry.getKey(),
                            windowStart,
                            windowEnd
                    );
                    continue;
                }

                if (normalizedWindowStart.isAfter(normalizedWindowEnd)) {
                    log.error(
                            "Skipping feature window because window_start is after window_end. datasetKey={}, equipmentId={}, windowStart={}, windowEnd={}",
                            datasetKey,
                            equipmentEntry.getKey(),
                            normalizedWindowStart,
                            normalizedWindowEnd
                    );
                    continue;
                }

                String resolvedEquipmentId = normalizeOptionalText(asString(
                        firstNonBlank(firstRow.get(EQUIPMENT_FIELD), firstRow.get("equipment_id")),
                        equipmentEntry.getKey()
                ));
                resolvedEquipmentId = canonicalizeEquipmentId(resolvedEquipmentId);

                Set<String> windowEquipmentIds = new LinkedHashSet<>();
                for (Document row : windowRows) {
                    String windowEquipmentId = canonicalizeEquipmentId(firstNonBlank(row.get(EQUIPMENT_FIELD), row.get("equipment_id")));
                    if (windowEquipmentId != null) {
                        windowEquipmentIds.add(windowEquipmentId);
                    }
                }
                if (windowEquipmentIds.size() > 1) {
                    log.warn(
                            "Skipping mixed-equipment feature window. datasetKey={}, windowStart={}, windowEnd={}, equipmentIds={}",
                            datasetKey,
                            normalizeTimestamp(windowStart),
                            normalizeTimestamp(windowEnd),
                            windowEquipmentIds
                    );
                    continue;
                }
                if (resolvedEquipmentId == null && windowEquipmentIds.size() == 1) {
                    resolvedEquipmentId = windowEquipmentIds.iterator().next();
                }
                if (resolvedEquipmentId == null) {
                    log.warn(
                            "Skipping feature window because equipment id cannot be resolved. datasetKey={}, windowStart={}, windowEnd={}",
                            datasetKey,
                            normalizeTimestamp(windowStart),
                            normalizeTimestamp(windowEnd)
                    );
                    continue;
                }

                Map<String, Object> meanValues = new LinkedHashMap<>();
                Map<String, Object> stdValues = new LinkedHashMap<>();
                Map<String, Object> minValues = new LinkedHashMap<>();
                Map<String, Object> maxValues = new LinkedHashMap<>();

                for (String column : selectedColumns) {
                    List<Double> numericValues = new ArrayList<>();
                    for (Document row : windowRows) {
                        Double value = toDouble(row.get(column));
                        if (value != null) {
                            numericValues.add(value);
                        }
                    }

                    if (numericValues.isEmpty()) {
                        meanValues.put(column, null);
                        stdValues.put(column, null);
                        minValues.put(column, null);
                        maxValues.put(column, null);
                        continue;
                    }

                    double sum = 0D;
                    double min = Double.POSITIVE_INFINITY;
                    double max = Double.NEGATIVE_INFINITY;
                    for (double value : numericValues) {
                        sum += value;
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                    }

                    double mean = sum / numericValues.size();
                    double variance = 0D;
                    for (double value : numericValues) {
                        double diff = value - mean;
                        variance += diff * diff;
                    }
                    variance /= numericValues.size();
                    double std = Math.sqrt(variance);

                    meanValues.put(column, mean);
                    stdValues.put(column, std);
                    minValues.put(column, min);
                    maxValues.put(column, max);
                }

                Map<String, Object> featureValues = new LinkedHashMap<>();
                featureValues.put("MEAN", meanValues);
                featureValues.put("STD", stdValues);
                featureValues.put("MIN", minValues);
                featureValues.put("MAX", maxValues);
                featureValues.put(
                        "META",
                        buildFeatureMeta(
                                datasetKey,
                                datasetName,
                                resolvedEquipmentId,
                                selectedColumns,
                                windowSize,
                                targetCollection,
                                sourceCollection,
                                matchFilter,
                                appliedMatchFilter
                        )
                );

                Document featureRow = new Document();
                featureRow.put("dataset_key", datasetKey);
                featureRow.put("dataset_name", datasetName);
                putIfPresent(featureRow, EQUIPMENT_FIELD, resolvedEquipmentId);
                putIfPresent(featureRow, "equipment_id", resolvedEquipmentId);
                putIfPresent(featureRow, "lot_no", firstNonBlank(firstRow.get("WORKORDER"), firstRow.get("lot_no")));
                putIfPresent(featureRow, "SOURCE_TYPE_CODE", firstNonBlank(firstRow.get("SOURCE_TYPE_CODE")));
                putIfPresent(featureRow, "SOURCE_DTL_CODE", firstNonBlank(firstRow.get("SOURCE_DTL_CODE")));
                putIfPresent(featureRow, "SOURCE_FILE", firstNonBlank(firstRow.get("SOURCE_FILE")));

                featureRow.put("window_start", windowStart);
                featureRow.put("window_end", windowEnd);
                featureRow.put("feature_values", featureValues);
                featureRow.put("REG_DATE", new Date());

                featureRows.add(featureRow);
                lastWindowEnd = normalizeTimestamp(windowEnd);
            }
        }

        return new FeatureWindowBuildResult(featureRows, lastWindowEnd);
    }

    private Map<String, Object> buildFeatureMeta(
            String datasetKey,
            String datasetName,
            String equipmentId,
            List<String> selectedColumns,
            int windowSize,
            String targetCollection,
            String sourceCollection,
            Document matchFilter,
            Document appliedMatchFilter
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("dataset_key", datasetKey);

        String datasetKeyHash = schemaResolver.datasetKeyHash(datasetKey);
        if (!datasetKeyHash.isBlank()) {
            meta.put("dataset_key_hash", datasetKeyHash);
        }
        if (datasetName != null && !datasetName.isBlank()) {
            meta.put("dataset_name", datasetName);
        }
        if (equipmentId != null && !equipmentId.isBlank()) {
            meta.put(EQUIPMENT_FIELD, equipmentId);
            meta.put("equipment_id", equipmentId);
        }

        meta.put("selected_columns", selectedColumns);
        meta.put("feature_stats", FEATURE_STAT_KEYS);
        meta.put("source_collection", sourceCollection);
        meta.put("target_collection", targetCollection);
        meta.put("timestamp_field", TIMESTAMP_FIELD);
        meta.put("equipment_id_field", EQUIPMENT_FIELD);
        meta.put("window_mode", "fixed_count_only");
        meta.put("window_size", windowSize);
        if (matchFilter != null && !matchFilter.isEmpty()) {
            meta.put("match_filter", cloneDocument(matchFilter));
        }
        if (appliedMatchFilter != null && !appliedMatchFilter.isEmpty()) {
            meta.put("applied_match_filter", cloneDocument(appliedMatchFilter));
        }
        return meta;
    }

    private FeatureAutoJobStatusDto toJobStatus(Document job) {
        if (job == null) {
            String defaultDatasetKey = resolveDefaultOperationalDatasetKey();
            return new FeatureAutoJobStatusDto(
                    defaultDatasetKey,
                    schemaResolver.buildDatasetLabel(defaultDatasetKey, DEFAULT_DATASET_NAME),
                    resolveTargetCollection(null),
                    defaultWindowSize,
                    List.of(),
                    defaultScheduleIntervalSeconds(),
                    false,
                    0,
                    null,
                    null,
                    null,
                    RUN_STATUS_IDLE,
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

        String datasetKey = resolvePolicyDatasetKeyForRead(job.get("dataset_key"), job.get("equipment_scope"));
        if (isLegacyOrDeprecatedRuntimeDataset(datasetKey)) {
            return null;
        }
        String sourceCollection = resolveSourceCollectionFromJob(job, datasetKey);
        String datasetName = asString(job.get("dataset_name"), sourceCollection);
        String lastProcessedRowId = asString(job.get("last_checkpoint_value"), null);
        long pendingRawCount = 0L;
        String statusOverride = null;
        String errorOverride = null;
        try {
            RawDatasetFilterSpec rawDatasetFilterSpec = buildRawDatasetFilter(job, datasetKey);
            pendingRawCount = rawPreviewRepository.countRowsByDatasetKeyAfterObjectId(
                    sourceCollection,
                    rawDatasetFilterSpec.rawDatasetFilter(),
                    lastProcessedRowId
            );
        } catch (IllegalStateException illegalStateException) {
            statusOverride = INVALID_CONFIG_STATUS;
            errorOverride = illegalStateException.getMessage();
        }

        String lastRunAt = asString(job.get("last_run_at"), null);
        return new FeatureAutoJobStatusDto(
                datasetKey,
                asString(job.get("dataset_label"), schemaResolver.buildDatasetLabel(datasetKey, datasetName)),
                resolveTargetCollection(asString(job.get("target_collection"), null)),
                resolveWindowSize(asInteger(job.get("window_size"))),
                asStringList(job.get("selected_columns")),
                resolveScheduleIntervalSeconds(asInteger(job.get("scheduler_interval_sec"))),
                USE_YN_ACTIVE.equalsIgnoreCase(asString(job.get("use_yn"), USE_YN_ACTIVE)),
                pendingRawCount,
                lastRunAt,
                lastProcessedRowId,
                asString(job.get("last_window_end"), null),
                statusOverride == null ? asString(job.get("last_status"), RUN_STATUS_IDLE) : statusOverride,
                null,
                lastRunAt,
                asString(job.get("last_trigger_type"), null),
                asInt(job.get("last_total_window_count"), 0),
                asInt(job.get("last_created_count"), 0),
                asInt(job.get("last_skipped_count"), 0),
                asInt(job.get("last_consumed_raw_count"), 0),
                errorOverride == null ? asString(job.get("last_error_message"), null) : errorOverride,
                asString(job.get("upd_date"), null)
        );
    }

    private boolean isRuntimeStatusTargetJob(Document job) {
        if (job == null) {
            return false;
        }
        if (!USE_YN_ACTIVE.equalsIgnoreCase(asString(job.get("use_yn"), USE_YN_INACTIVE))) {
            return false;
        }
        if (!asBoolean(job.get("scheduler_enabled"), false)) {
            return false;
        }
        String datasetKey = resolvePolicyDatasetKeyForRead(job.get("dataset_key"), job.get("equipment_scope"));
        return equipmentMasterService.isRuntimeOperationalDatasetKey(datasetKey);
    }

    private boolean isRuntimeExecutionTargetJob(Document job) {
        if (job == null) {
            return false;
        }
        if (!USE_YN_ACTIVE.equalsIgnoreCase(asString(job.get("use_yn"), USE_YN_INACTIVE))) {
            return false;
        }
        if (!asBoolean(job.get("scheduler_enabled"), false)) {
            return false;
        }
        String datasetKey = resolvePolicyDatasetKeyForRead(job.get("dataset_key"), job.get("equipment_scope"));
        return equipmentMasterService.isRuntimeOperationalDatasetKey(datasetKey);
    }

    private boolean isLegacyOrDeprecatedRuntimeDataset(String datasetKey) {
        return equipmentMasterService.isLegacyGlobalDatasetKey(datasetKey)
                || equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey);
    }

    private RawDatasetFilterSpec buildRawDatasetFilter(Document job, String datasetKey) {
        Document matchFilter = resolveMatchFilter(job, datasetKey);
        String datasetEquipmentId = equipmentMasterService.resolveEquipmentIdByDatasetKey(datasetKey);
        boolean runtimeOperationalDataset = equipmentMasterService.isRuntimeOperationalDatasetKey(datasetKey);

        if (runtimeOperationalDataset) {
            if (matchFilter == null || matchFilter.isEmpty()) {
                throw new IllegalStateException(
                        "tmst_dataset_config.match_filter is required and must include MCCODE for dataset_key=" + datasetKey
                );
            }
            String matchFilterEquipmentId = resolveMatchFilterEquipmentId(matchFilter);
            if (matchFilterEquipmentId == null) {
                throw new IllegalStateException(
                        "tmst_dataset_config.match_filter.MCCODE is required for dataset_key=" + datasetKey
                );
            }
            if (!datasetEquipmentId.equalsIgnoreCase(matchFilterEquipmentId)) {
                throw new IllegalStateException(
                        "tmst_dataset_config.match_filter.MCCODE mismatch. dataset_key="
                                + datasetKey
                                + ", expected="
                                + datasetEquipmentId
                                + ", actual="
                                + matchFilterEquipmentId
                );
            }
        }
        Document appliedMatchFilter = cloneDocument(matchFilter);

        List<Document> andClauses = new ArrayList<>();
        if (appliedMatchFilter != null && !appliedMatchFilter.isEmpty()) {
            andClauses.add(cloneDocument(appliedMatchFilter));
        }

        boolean alreadyHasEquipmentFilter = resolveMatchFilterEquipmentId(appliedMatchFilter) != null;

        if (!alreadyHasEquipmentFilter) {
            String equipmentScope = datasetEquipmentId == null
                    ? schemaResolver.resolveEquipmentScopeFromDatasetKey(datasetKey)
                    : datasetEquipmentId;

            if (equipmentScope != null
                    && !equipmentScope.isBlank()
                    && !"all".equalsIgnoreCase(equipmentScope)
                    && !"default".equalsIgnoreCase(equipmentScope)) {
                andClauses.add(new Document(EQUIPMENT_FIELD, equipmentScope));
            }
        }

        Map<String, Object> rawDatasetFilter = new LinkedHashMap<>();
        if (andClauses.size() == 1) {
            rawDatasetFilter.putAll(andClauses.get(0));
        } else if (andClauses.size() > 1) {
            rawDatasetFilter.put("$and", andClauses);
        }

        return new RawDatasetFilterSpec(rawDatasetFilter, matchFilter, appliedMatchFilter);
    }

    private String resolveMatchFilterEquipmentId(Document matchFilter) {
        if (matchFilter == null || matchFilter.isEmpty()) {
            return null;
        }
        Object rawMccode = matchFilter.get(EQUIPMENT_FIELD);
        if (rawMccode instanceof String textMccode) {
            return normalizeOptionalText(textMccode);
        }
        if (rawMccode instanceof Document mccodeDocument) {
            Object eqValue = mccodeDocument.get("$eq");
            if (eqValue instanceof String textEqValue) {
                return normalizeOptionalText(textEqValue);
            }
        }
        return null;
    }

    private Document resolveMatchFilter(Document policyOrJob, String datasetKey) {
        Document matchFilter = extractMatchFilter(policyOrJob);
        if (matchFilter != null && !matchFilter.isEmpty()) {
            return matchFilter;
        }

        Document datasetConfig = featureAutoJobRepository.findDatasetConfigByDatasetKey(datasetKey);
        Document configMatchFilter = extractMatchFilter(datasetConfig);
        if (configMatchFilter == null || configMatchFilter.isEmpty()) {
            return null;
        }
        return configMatchFilter;
    }

    private Document extractMatchFilter(Document policyOrJob) {
        Object rawMatchFilter = policyOrJob == null ? null : policyOrJob.get("match_filter");
        if (rawMatchFilter instanceof Document document) {
            return cloneDocument(document);
        }
        if (rawMatchFilter instanceof Map<?, ?> rawMap) {
            return toDocument(rawMap);
        }
        return null;
    }

    private String resolvePolicyDatasetKeyForSave(String requestedDatasetKey, String equipmentId) {
        String normalized = schemaResolver.normalizeDatasetKeyString(requestedDatasetKey);
        if (normalized == null || SOURCE_COLLECTION.equalsIgnoreCase(normalized)) {
            String fromEquipment = equipmentMasterService.resolveDatasetKeyByEquipment(equipmentId);
            if (fromEquipment != null) {
                return fromEquipment;
            }
            return resolveDefaultOperationalDatasetKey();
        }
        DataTypeDatasetDocument datasetDocument =
                dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(normalized, USE_YN_ACTIVE);
        if (datasetDocument != null) {
            return normalized;
        }
        return normalized;
    }

    private List<String> resolveNumericColumns(List<Document> rawRows, List<String> selectedColumns) {
        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }
        List<String> numericColumns = new ArrayList<>();
        int totalRows = Math.max(rawRows.size(), 1);
        for (String column : selectedColumns) {
            int nonNullNumericCount = 0;
            for (Document row : rawRows) {
                if (toDouble(row.get(column)) != null) {
                    nonNullNumericCount++;
                }
            }
            double nonNullRatio = nonNullNumericCount / (double) totalRows;
            if (nonNullNumericCount > 0 && nonNullRatio >= MIN_NON_NULL_RATIO_FOR_FEATURE) {
                numericColumns.add(column);
                continue;
            }
            log.info(
                    "Feature-auto selected column excluded due to low non-null ratio. column={}, nonNullCount={}, totalRows={}, nonNullRatio={}",
                    column,
                    nonNullNumericCount,
                    totalRows,
                    nonNullRatio
            );
        }
        return numericColumns;
    }

    private String resolvePolicyDatasetKeyForRead(Object rawDatasetKey, Object fallbackEquipmentId) {
        String normalized = schemaResolver.normalizeDatasetKeyString(rawDatasetKey);
        if (normalized == null || SOURCE_COLLECTION.equalsIgnoreCase(normalized)) {
            String fromEquipment = equipmentMasterService.resolveDatasetKeyByEquipment(asString(fallbackEquipmentId, null));
            if (fromEquipment != null) {
                return fromEquipment;
            }
            return resolveDefaultOperationalDatasetKey();
        }
        DataTypeDatasetDocument datasetDocument =
                dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(normalized, USE_YN_ACTIVE);
        if (datasetDocument != null || schemaResolver.isDatasetKeyPolicyFormat(normalized)) {
            return normalized;
        }
        return normalized;
    }

    private String resolveSourceCollectionFromJob(Document job, String datasetKey) {
        String sourceCollection = asString(job.get("source_collection"), null);
        if (sourceCollection != null && !sourceCollection.isBlank()) {
            return sourceCollection.trim();
        }
        return resolveSourceCollectionForDatasetKey(datasetKey);
    }

    private String resolveSourceCollectionForDatasetKey(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return SOURCE_COLLECTION;
        }

        DataTypeDatasetDocument datasetDocument = dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(normalizedDatasetKey, USE_YN_ACTIVE);
        if (datasetDocument != null) {
            return resolveSourceCollectionName(datasetDocument.getSourceCollection());
        }

        String[] tokens = normalizedDatasetKey.split("_");
        if (tokens.length == 0) {
            return SOURCE_COLLECTION;
        }
        return SOURCE_COLLECTION;
    }

    private String resolveSourceCollectionName(String sourceCollection) {
        if (sourceCollection == null || sourceCollection.isBlank()) {
            return SOURCE_COLLECTION;
        }
        return sourceCollection.trim();
    }

    private List<String> normalizeSelectedColumns(List<String> selectedColumns) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            throw new IllegalArgumentException("selected_columns must not be empty.");
        }

        return selectedColumns.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private int resolveWindowSize(Integer requestedWindowSize) {
        int fallbackWindowSize = Math.max(defaultWindowSize, 1);
        if (requestedWindowSize == null) {
            return fallbackWindowSize;
        }
        if (requestedWindowSize <= 0) {
            throw new IllegalArgumentException("window_size must be greater than 0.");
        }
        return requestedWindowSize;
    }

    private int resolveScheduleIntervalSeconds(Integer requestedIntervalSeconds) {
        int fallback = defaultScheduleIntervalSeconds();
        if (requestedIntervalSeconds == null) {
            return fallback;
        }
        if (requestedIntervalSeconds <= 0) {
            throw new IllegalArgumentException("scheduler_interval_sec must be greater than 0.");
        }
        return requestedIntervalSeconds;
    }

    private int defaultScheduleIntervalSeconds() {
        long seconds = Math.max(schedulerFixedDelayMs / 1000L, 1L);
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }

    private String resolveTargetCollection(String targetCollection) {
        if (targetCollection == null || targetCollection.isBlank()) {
            return defaultTargetCollection == null || defaultTargetCollection.isBlank()
                    ? "thisfeature"
                    : defaultTargetCollection.trim();
        }
        return targetCollection.trim();
    }

    private String resolveUseYn(Boolean useYn) {
        if (useYn == null) {
            return USE_YN_ACTIVE;
        }
        return useYn ? USE_YN_ACTIVE : USE_YN_INACTIVE;
    }

    private String extractObjectId(Object value) {
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeTimestamp(Object value) {
        Instant normalizedInstant = normalizeTimestampAsInstant(value);
        return normalizedInstant == null ? null : normalizedInstant.toString();
    }

    private Instant normalizeTimestampAsInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instantValue) {
            return instantValue;
        }
        if (value instanceof Date dateValue) {
            return dateValue.toInstant();
        }
        if (value instanceof Number numberValue) {
            long epochMillis = numberValue.longValue();
            if (epochMillis <= 0L) {
                return null;
            }
            return Instant.ofEpochMilli(epochMillis);
        }

        Object normalizedValue = schemaResolver.normalizeResponseValue(value);
        if (normalizedValue instanceof Instant instantValue) {
            return instantValue;
        }
        if (normalizedValue instanceof Date dateValue) {
            return dateValue.toInstant();
        }

        String normalized = normalizedValue == null ? null : normalizedValue.toString().trim();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return fallback;
            }
            return "true".equalsIgnoreCase(normalized) || "y".equalsIgnoreCase(normalized);
        }
        return fallback;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private int asInt(Object value, int fallback) {
        Integer parsed = asInteger(value);
        return parsed == null ? fallback : parsed;
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }

        return rawList.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private Document toDocument(Map<?, ?> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return new Document();
        }

        Document document = new Document();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            document.put(entry.getKey().toString(), cloneValue(entry.getValue()));
        }
        return document;
    }

    private Document cloneDocument(Document source) {
        if (source == null) {
            return null;
        }
        return toDocument(source);
    }

    private Object cloneValue(Object value) {
        if (value instanceof Document documentValue) {
            return cloneDocument(documentValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return toDocument(mapValue);
        }
        if (value instanceof List<?> listValue) {
            List<Object> clonedList = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                clonedList.add(cloneValue(item));
            }
            return clonedList;
        }
        return value;
    }

    private void putIfPresent(Document targetDocument, String fieldName, Object value) {
        Object normalized = normalizeSimpleValue(value);
        if (normalized != null) {
            targetDocument.put(fieldName, normalized);
        }
    }

    private Object firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }

        for (Object value : values) {
            Object normalized = normalizeSimpleValue(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private Object normalizeSimpleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String textValue) {
            String trimmed = textValue.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return value;
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String textValue) {
            String trimmed = textValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveDefaultOperationalDatasetKey() {
        String fromEquipmentMaster = equipmentMasterService.resolveDefaultOperationalDatasetKey();
        if (fromEquipmentMaster != null) {
            return fromEquipmentMaster;
        }
        String fromConfig = schemaResolver.resolveRuntimeDefaultDatasetKey();
        if (fromConfig != null) {
            return fromConfig;
        }
        return LEGACY_GLOBAL_DATASET_KEY;
    }

    private String normalizeOptionalText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<String, List<Document>> partitionRawRowsByEquipment(List<Document> rawRows) {
        Map<String, List<Document>> partitioned = new LinkedHashMap<>();
        for (Document rawRow : rawRows) {
            String equipmentId = canonicalizeEquipmentId(firstNonBlank(rawRow.get(EQUIPMENT_FIELD), rawRow.get("equipment_id")));
            String key = equipmentId == null ? "__UNKNOWN__" : equipmentId;
            partitioned.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rawRow);
        }
        for (List<Document> equipmentRows : partitioned.values()) {
            equipmentRows.sort(
                    Comparator.comparing(
                                    (Document row) -> asString(row.get(TIMESTAMP_FIELD), "")
                            )
                            .thenComparing(row -> asString(row.get("_id"), ""))
            );
        }
        return partitioned;
    }

    private String canonicalizeEquipmentId(Object value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private record RawDatasetFilterSpec(
            Map<String, Object> rawDatasetFilter,
            Document matchFilter,
            Document appliedMatchFilter
    ) {
    }

    private record FeatureWindowBuildResult(
            List<Document> featureRows,
            String lastWindowEnd
    ) {
    }

    private record JobRunOutcome(
            FeatureAutoTriggerResultDto result,
            boolean success
    ) {
    }
}
