package com.demo.insight.modeltrain.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.demo.insight.algorithm.dto.AlgorithmParamDto;
import com.demo.insight.algorithm.repository.ParamRepository;
import com.demo.insight.anomalycause.AnomalyCauseService;
import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.equipment.service.EquipmentMasterService;
import com.demo.insight.modeltrain.client.AiInferenceResult;
import com.demo.insight.modeltrain.client.AiModelExecutionClient;
import com.demo.insight.modeltrain.client.AiRandomForestExecutionResult;
import com.demo.insight.modeltrain.client.AiRandomForestFeatureImportanceResult;
import com.demo.insight.modeltrain.client.AiRandomForestPredictionResult;
import com.demo.insight.modeltrain.dto.AiOverviewActiveModelDto;
import com.demo.insight.modeltrain.dto.AiOverviewAnomalySummaryDto;
import com.demo.insight.modeltrain.dto.AiOverviewDatasetModelDto;
import com.demo.insight.modeltrain.dto.AiOverviewFeatureImportanceDto;
import com.demo.insight.modeltrain.dto.AiOverviewFeatureSummaryDto;
import com.demo.insight.modeltrain.dto.AiOverviewLabeledDataSummaryDto;
import com.demo.insight.modeltrain.dto.AiOverviewLatestRunDto;
import com.demo.insight.modeltrain.dto.AiOverviewResponseDto;
import com.demo.insight.modeltrain.dto.AiOverviewSupervisedSummaryDto;
import com.demo.insight.modeltrain.dto.AnomalyAlgorithmOptionDto;
import com.demo.insight.modeltrain.dto.AnomalyResultListResponseDto;
import com.demo.insight.modeltrain.dto.AnomalyResultPointDto;
import com.demo.insight.modeltrain.dto.AnomalyResultQueryResponseDto;
import com.demo.insight.modeltrain.dto.AnomalyResultSummaryDto;
import com.demo.insight.modeltrain.dto.AnomalyRunDetailDto;
import com.demo.insight.modeltrain.dto.AnomalyRunListResponseDto;
import com.demo.insight.modeltrain.dto.AnomalyRunOptionDto;
import com.demo.insight.modeltrain.dto.CreateModelRunRequestDto;
import com.demo.insight.modeltrain.dto.ExecuteModelRunRequestDto;
import com.demo.insight.modeltrain.dto.ExecuteModelRunResponseDto;
import com.demo.insight.modeltrain.dto.FeatureDatasetDto;
import com.demo.insight.modeltrain.dto.FeatureDatasetListResponseDto;
import com.demo.insight.modeltrain.dto.HomeInsightRecentWindowDto;
import com.demo.insight.modeltrain.dto.HomeInsightResponseDto;
import com.demo.insight.modeltrain.dto.HomeInsightTopSensorDto;
import com.demo.insight.modeltrain.dto.HomeInsightTrendPointDto;
import com.demo.insight.modeltrain.dto.ModelRunDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyListResponseDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyStatusDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyUpsertRequestDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoTriggerRequestDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoTriggerResponseDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoTriggerResultDto;
import com.demo.insight.modeltrain.repository.ModelTrainRepository;
import com.demo.insight.thresholdalert.ThresholdAlertService;

@Service
public class ModelTrainServiceImpl implements ModelTrainService {

    private static final Logger log = LoggerFactory.getLogger(ModelTrainServiceImpl.class);

    private static final String READY_STATUS = "READY";
    private static final String RUNNING_STATUS = "RUNNING";
    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String FAIL_STATUS = "FAIL";
    private static final String SKIPPED_STATUS = "SKIPPED";

    private static final String NORMAL_STATUS = "NORMAL";
    private static final String WARNING_STATUS = "WARNING";
    private static final String CRITICAL_STATUS = "CRITICAL";
    private static final String NO_DATA_STATUS = "NO_DATA";
    private static final String OVERVIEW_SUMMARY_TYPE_UNSUPERVISED = "UNSUPERVISED";
    private static final String OVERVIEW_SUMMARY_TYPE_SUPERVISED = "SUPERVISED";
    private static final String CORRECT_YN_Y = "Y";
    private static final String CORRECT_YN_N = "N";
    private static final int OVERVIEW_FEATURE_IMPORTANCE_LIMIT = 5;

    private static final String ALGO_ISOLATION_FOREST = "ISOLATION_FOREST";
    private static final String ALGO_AUTOENCODER = "AUTOENCODER";
    private static final String ALGO_RANDOM_FOREST = "RANDOM_FOREST";
    private static final String POLICY_RANDOM_FOREST_SUPERVISED_V1 = "POLICY_RANDOM_FOREST_SUPERVISED_V1";
    private static final String DATASET_THISRAWLABELED_ALL_RF_V1 = "thisrawlabeled_all_rf_v1";
    private static final String LABELED_SOURCE_COLLECTION = "thisrawlabeled";
    private static final String LABELED_DATA_NOT_READY_LABEL = "Labeled data is not ready";
    private static final String INTERNAL_DATASET_KEY_PARAM = "_dataset_key";
    private static final String DOC_TYPE_FIELD = "doc_type";
    private static final String DOC_TYPE_RUN = "RUN";
    private static final String TRIGGER_TYPE_MANUAL = "MANUAL";
    private static final String TRIGGER_TYPE_SCHEDULE = "SCHEDULE";
    private static final String POLICY_STATUS_IDLE = "IDLE";
    private static final String ACTIVE_USE_FLAG = "Y";
    private static final String LEGACY_GLOBAL_DATASET_KEY = "demo_hmi_all_default_v1";
    private static final String DEFAULT_DATASET_NAME = "Demo HMI Dataset";
    private static final String DEFAULT_SOURCE_COLLECTION = "THISHMIDATA";
    private static final String EQUIPMENT_FIELD = "MCCODE";

    private static final int DEFAULT_RESULT_LIMIT = 1000;
    private static final int MAX_RESULT_LIMIT = 2000;
    private static final int DEFAULT_RUN_LIST_LIMIT = 50;
    private static final int MAX_RUN_LIST_LIMIT = 500;
    private static final int DEFAULT_WINDOW_SIZE = 100;
    private static final int AI_EXECUTE_BATCH_SIZE = 200;
    private static final int HOME_LOOKBACK_DAYS = 7;
    private static final int HOME_TOP_SENSOR_LIMIT = 5;
    private static final int HOME_RECENT_WINDOW_LIMIT = 3;
    private static final int HOME_TOP_CAUSE_LIMIT = 3;
    private static final ZoneOffset HOME_ZONE_OFFSET = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter HOME_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private static final double WARNING_SCORE_THRESHOLD = 0.5D;
    private static final double CRITICAL_SCORE_THRESHOLD = 0.8D;
    private static final double AE_WARNING_RISK_THRESHOLD = 0.45D;
    private static final double AE_CRITICAL_RISK_THRESHOLD = 0.75D;
    private static final double AE_ZSCORE_SCALE = 1.8D;
    private static final double AE_ZSCORE_WEIGHT = 0.65D;
    private static final double AE_MINMAX_WEIGHT = 0.35D;
    private static final double SCORE_STD_EPSILON = 1e-9D;
    private static final double INTEGRATED_WARNING_HEALTH_THRESHOLD = 0.80D;
    private static final double INTEGRATED_CRITICAL_HEALTH_THRESHOLD = 0.50D;
    private static final double IF_INTEGRATED_WEIGHT = 0.5D;
    private static final double AE_INTEGRATED_WEIGHT = 0.5D;
    private static final List<String> OVERVIEW_STATUS_ORDER = List.of(
            NORMAL_STATUS,
            WARNING_STATUS,
            CRITICAL_STATUS,
            NO_DATA_STATUS
    );

    private static final List<String> FEATURE_STAT_KEYS = List.of("MEAN", "STD", "MIN", "MAX");

    private static final String PARAM_CONTAMINATION = "CONTAMINATION";
    private static final String PARAM_N_ESTIMATORS = "N_ESTIMATORS";
    private static final String PARAM_MAX_SAMPLES = "MAX_SAMPLES";
    private static final String PARAM_SEED = "SEED";
    private static final String PARAM_SEQUENCE_LENGTH = "SEQUENCE_LENGTH";
    private static final String PARAM_HIDDEN_UNITS = "HIDDEN_UNITS";
    private static final String PARAM_LATENT_DIM = "LATENT_DIM";
    private static final String PARAM_BATCH_SIZE = "BATCH_SIZE";
    private static final String PARAM_EPOCH = "EPOCH";
    private static final String PARAM_DROPOUT = "DROPOUT";
    private static final String PARAM_LEARNING_RATE = "LEARNING_RATE";
    private static final String PARAM_EARLY_STOPPING = "EARLY_STOPPING";
    private static final String PARAM_PATIENCE = "PATIENCE";
    private static final String PARAM_HYPERPARAM_OPT_METHOD = "HYPERPARAM_OPT_METHOD";
    private static final String PARAM_TRAIN_VALID_RATIO = "TRAIN_VALID_RATIO";
    private static final String PARAM_RETRAIN_CYCLE = "RETRAIN_CYCLE";
    private static final String PARAM_MAX_DEPTH = "MAX_DEPTH";
    private static final String PARAM_MIN_SAMPLES_SPLIT = "MIN_SAMPLES_SPLIT";
    private static final String PARAM_MIN_SAMPLES_LEAF = "MIN_SAMPLES_LEAF";
    private static final String PARAM_MAX_FEATURES = "MAX_FEATURES";
    private static final String PARAM_CLASS_WEIGHT = "CLASS_WEIGHT";
    private static final String PARAM_TRAIN_SAMPLE_LIMIT = "TRAIN_SAMPLE_LIMIT";
    private static final String PARAM_LABEL_FIELD = "LABEL_FIELD";
    private static final String PARAM_LABEL_VERSION = "LABEL_VERSION";
    private static final String REQUIRED_YN = "Y";

    private static final int RF_DEFAULT_N_ESTIMATORS = 250;
    private static final int RF_DEFAULT_MAX_DEPTH = 10;
    private static final int RF_DEFAULT_MIN_SAMPLES_SPLIT = 2;
    private static final int RF_DEFAULT_MIN_SAMPLES_LEAF = 1;
    private static final String RF_DEFAULT_MAX_FEATURES = "sqrt";
    private static final String RF_DEFAULT_CLASS_WEIGHT = "balanced";
    private static final String RF_DEFAULT_TRAIN_VALID_RATIO = "8:2";
    private static final double RF_DEFAULT_TRAIN_VALID_RATIO_NUMERIC = 0.8D;
    private static final int RF_DEFAULT_SEED = 42;
    private static final String RF_DEFAULT_LABEL_FIELD = "label";
    private static final String RF_DEFAULT_LABEL_VERSION = "LABEL_V1";
    private static final String IF_DEFAULT_CONTAMINATION = "auto";
    private static final int IF_DEFAULT_N_ESTIMATORS = 100;
    private static final String IF_DEFAULT_MAX_SAMPLES = "auto";
    private static final int IF_DEFAULT_SEED = 42;
    private static final int AE_DEFAULT_EPOCH = 120;
    private static final double AE_DEFAULT_LEARNING_RATE = 0.001D;
    private static final double AE_DEFAULT_TRAIN_VALID_RATIO = 0.8D;
    private static final boolean AE_DEFAULT_EARLY_STOPPING = true;
    private static final int AE_DEFAULT_PATIENCE = 10;
    private static final double AE_DEFAULT_CONTAMINATION = 0.05D;
    private static final int AE_DEFAULT_SEED = 42;
    private static final List<String> RF_EXCLUDED_COLUMNS = List.of(
            "_id",
            "source_id",
            "PRDTIME",
            "timestamp",
            "MCCODE",
            "equipment_id",
            "label",
            "label_name",
            "label_source",
            "label_version",
            "label_reason",
            "label_reg_date",
            "regime",
            "cycle_gap"
    );

    private static final DateTimeFormatter RUN_ID_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final ModelTrainRepository modelTrainRepository;
    private final ParamRepository paramRepository;
    private final AiModelExecutionClient aiModelExecutionClient;
    private final DynamicSchemaResolver schemaResolver;
    private final EquipmentMasterService equipmentMasterService;
    private final AnomalyCauseService anomalyCauseService;
    private final ThresholdAlertService thresholdAlertService;
    private final ReentrantLock autoRunLock = new ReentrantLock();

    @Value("${app.modeltrain.auto.enabled:true}")
    private boolean autoSchedulerEnabled;

    @Value("${app.modeltrain.auto.fixed-delay-ms:300000}")
    private long autoSchedulerFixedDelayMs;

    @Value("${app.modeltrain.auto.default-scheduler-interval-sec:600}")
    private int defaultSchedulerIntervalSec;

    @Value("${app.modeltrain.auto.default-min-new-feature-count:50}")
    private int defaultMinNewFeatureCount;

    @Value("${app.modeltrain.auto.default-min-total-feature-count:200}")
    private int defaultMinTotalFeatureCount;

    @Value("${app.modeltrain.supervised.default-sample-limit:50000}")
    private int defaultSupervisedSampleLimit;

    public ModelTrainServiceImpl(
            ModelTrainRepository modelTrainRepository,
            ParamRepository paramRepository,
            AiModelExecutionClient aiModelExecutionClient,
            DynamicSchemaResolver schemaResolver,
            EquipmentMasterService equipmentMasterService,
            AnomalyCauseService anomalyCauseService,
            ThresholdAlertService thresholdAlertService
    ) {
        this.modelTrainRepository = modelTrainRepository;
        this.paramRepository = paramRepository;
        this.aiModelExecutionClient = aiModelExecutionClient;
        this.schemaResolver = schemaResolver;
        this.equipmentMasterService = equipmentMasterService;
        this.anomalyCauseService = anomalyCauseService;
        this.thresholdAlertService = thresholdAlertService;
    }

    @Override
    public ModelRunDto createModelRun(CreateModelRunRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String datasetKey = resolveDatasetKeyForPolicy(
                request.datasetKey(),
                request.equipmentId(),
                request.sensorId()
        );
        if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)) {
            throw new IllegalArgumentException("Deprecated dataset_key is not allowed for model runtime: " + datasetKey);
        }

        String equipmentId = resolveEquipmentId(datasetKey, request.equipmentId());
        String sensorId = resolveSensorId(datasetKey, request.sensorId());
        FeatureConfigContext featureConfig = resolveFeatureConfigContext(datasetKey, "MODEL_RUN_CREATE");
        List<String> selectedColumns = resolveFeatureSelectedColumns(featureConfig);
        if (selectedColumns.isEmpty()) {
            selectedColumns = normalizeSelectedColumns(request.selectedColumns());
        }
        Integer configuredWindowSize = resolveFeatureWindowSizeOrNull(featureConfig);
        int windowSize = configuredWindowSize == null
                ? resolveWindowSize(request.windowSize())
                : resolveWindowSize(configuredWindowSize);
        String datasetName = resolveFeatureDatasetName(featureConfig);
        String sourceCollection = firstNonBlank(resolveFeatureSourceCollection(featureConfig), DEFAULT_SOURCE_COLLECTION);
        String targetCollection = firstNonBlank(resolveFeatureTargetCollection(featureConfig), "thisfeature");
        String algoCode = normalizeRequiredText(request.algoCode(), "algo_code");
        String algoName = normalizeRequiredText(request.algoName(), "algo_name");
        Map<String, Object> params = normalizeParams(request.params());

        String runId = generateUniqueRunId();
        Date regDate = new Date();
        Document document = buildRunDocument(
                runId,
                datasetKey,
                equipmentId,
                sensorId,
                selectedColumns,
                windowSize,
                datasetName,
                sourceCollection,
                targetCollection,
                algoCode,
                algoName,
                params,
                null,
                TRIGGER_TYPE_MANUAL,
                null,
                featureConfig.configSource(),
                featureConfig.configMessage()
        );
        document.put("reg_date", regDate);
        modelTrainRepository.insertModelRun(document);

        return new ModelRunDto(
                runId,
                equipmentId,
                sensorId,
                datasetKey,
                selectedColumns,
                windowSize,
                algoCode,
                algoName,
                params,
                READY_STATUS,
                regDate.toInstant().toString()
        );
    }

    @Override
    public ExecuteModelRunResponseDto executeModelRun(ExecuteModelRunRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String runId = normalizeRequiredText(request.runId(), "run_id");
        return executeModelRunInternal(runId);
    }

    @Override
    public ModelTrainAutoPolicyListResponseDto getModelTrainAutoPolicies(String datasetKey) {
        String datasetKeyFilter = resolveOptionalDatasetKey(datasetKey);
        List<Document> policyDocuments = datasetKeyFilter == null
                ? modelTrainRepository.findAllPolicies()
                : modelTrainRepository.findPoliciesByDatasetKey(datasetKeyFilter);
        List<ModelTrainAutoPolicyStatusDto> policies = policyDocuments.stream()
                .map(this::toAutoPolicyStatus)
                .toList();

        return new ModelTrainAutoPolicyListResponseDto(
                autoSchedulerEnabled,
                autoSchedulerFixedDelayMs,
                policies
        );
    }

    @Override
    public ModelTrainAutoPolicyStatusDto upsertModelTrainAutoPolicy(ModelTrainAutoPolicyUpsertRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String datasetKey = resolveDatasetKeyForPolicy(
                request.datasetKey(),
                request.equipmentId(),
                request.sensorId()
        );
        if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)) {
            throw new IllegalArgumentException("Deprecated dataset_key is not allowed for model runtime: " + datasetKey);
        }
        // Backward-compatible request validation only.
        // Runtime source-of-truth for feature settings is tmst_feature_mst.
        normalizeSelectedColumns(request.selectedColumns());
        resolveWindowSize(request.windowSize());

        String algoCode = normalizeSupportedAlgoCode(request.algoCode(), true);
        String algoName = normalizeRequiredText(request.algoName(), "algo_name");
        Map<String, Object> params = resolvePolicyParamsSnapshot(algoCode, request.params());

        boolean autoTrainEnabled = request.autoTrainEnabled() == null || request.autoTrainEnabled();
        int schedulerIntervalSec = resolveSchedulerIntervalSec(request.schedulerIntervalSec());
        int schedulerIntervalMinutes = Math.max((int) Math.ceil(schedulerIntervalSec / 60.0D), 1);
        int minNewFeatureCount = resolveMinNewFeatureCount(request.minNewFeatureCount());
        int minTotalFeatureCount = resolveMinTotalFeatureCount(request.minTotalFeatureCount());
        Integer recentWindowLimit = resolveRecentWindowLimit(request.recentWindowLimit());

        String policyId = resolvePolicyIdForUpsert(datasetKey, algoCode);
        Document existingPolicy = modelTrainRepository.findPolicyByPolicyId(policyId);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("policy_id", policyId);
        fields.put("dataset_key", datasetKey);
        fields.put("algo_code", algoCode);
        fields.put("algo_name", algoName);
        fields.put("params", params);
        fields.put("auto_train_enabled", autoTrainEnabled);
        fields.put("scheduler_interval_minutes", schedulerIntervalMinutes);
        fields.put("min_new_feature_count", minNewFeatureCount);
        fields.put("min_total_feature_count", minTotalFeatureCount);
        fields.put("recent_window_limit", recentWindowLimit);
        fields.put("description", asString(request.datasetName(), schemaResolver.buildDatasetLabel(datasetKey, DEFAULT_DATASET_NAME)));
        fields.put("use_flag", ACTIVE_USE_FLAG);
        if (existingPolicy == null) {
            fields.put("reg_date", new Date());
        }

        Document policyDocument = modelTrainRepository.upsertPolicy(policyId, fields);
        upsertActiveSelection(datasetKey, policyDocument, "MODELTRAIN_POLICY_SAVE");
        return toAutoPolicyStatus(policyDocument);
    }

    @Override
    public ModelTrainAutoTriggerResponseDto triggerModelTrainAutoPolicies(ModelTrainAutoTriggerRequestDto request) {
        if (!isAdminTriggerRequest(request)) {
            throw new IllegalArgumentException("Only admin can run manual model execution.");
        }

        boolean forceRun = request == null || request.forceRun() == null || request.forceRun();
        List<Document> requestedPolicies = resolveRequestedPoliciesForTrigger(request);
        if (requestedPolicies.isEmpty()) {
            return new ModelTrainAutoTriggerResponseDto(0, 0, 0, List.of());
        }

        if (!autoRunLock.tryLock()) {
            List<ModelTrainAutoTriggerResultDto> busyResults = requestedPolicies.stream()
                    .map(policy -> new ModelTrainAutoTriggerResultDto(
                            asString(policy.get("policy_id"), ""),
                            asString(policy.get("dataset_label"), "(unclassified dataset)"),
                            "SKIPPED_BUSY",
                            "Another model-train auto run is already in progress.",
                            null,
                            0,
                            0,
                            0,
                            0
                    ))
                    .toList();
            return new ModelTrainAutoTriggerResponseDto(
                    requestedPolicies.size(),
                    0,
                    0,
                    busyResults
            );
        }

        try {
            List<PolicyRunOutcome> outcomes = new ArrayList<>();
            for (Document policy : requestedPolicies) {
                try {
                    outcomes.add(executePolicy(policy, TRIGGER_TYPE_MANUAL, forceRun));
                } catch (Exception exception) {
                    log.error("Model-train manual trigger failed for policy entry.", exception);
                    outcomes.add(buildUnhandledPolicyFailureOutcome(policy, exception));
                }
            }

            int successCount = (int) outcomes.stream().filter(PolicyRunOutcome::success).count();
            List<ModelTrainAutoTriggerResultDto> results = outcomes.stream()
                    .map(PolicyRunOutcome::result)
                    .toList();

            return new ModelTrainAutoTriggerResponseDto(
                    requestedPolicies.size(),
                    outcomes.size(),
                    successCount,
                    results
            );
        } finally {
            autoRunLock.unlock();
        }
    }

    @Override
    public void runScheduledModelTrainAutoPolicies() {
        if (!autoSchedulerEnabled) {
            return;
        }

        if (!autoRunLock.tryLock()) {
            log.info("Skipping model-train auto scheduler run because another run is in progress.");
            return;
        }

        try {
            List<Document> activeSelections = modelTrainRepository.findEnabledModelActives();
            if (activeSelections.isEmpty()) {
                log.info("Skipping model-train auto scheduler run because tmst_model_active(use_flag=Y) has no executable dataset.");
                return;
            }

            Instant now = Instant.now();
            int executedCount = 0;
            int successCount = 0;

            for (Document activeSelection : activeSelections) {
                try {
                    String datasetKey = resolveDatasetKeyForPolicy(
                            normalizeOptionalText(activeSelection.get("dataset_key")),
                            null,
                            null
                    );
                    if (!equipmentMasterService.isRuntimeOperationalDatasetKey(datasetKey)) {
                        log.info(
                                "Skipping scheduler dataset because dataset is not an active AI operational dataset. dataset_key={}",
                                datasetKey
                        );
                        continue;
                    }
                    String activePolicyId = normalizeOptionalText(activeSelection.get("active_policy_id"));
                    if (activePolicyId == null) {
                        log.info("Skipping scheduler dataset because active_policy_id is missing. dataset_key={}", datasetKey);
                        continue;
                    }

                    Document policy = modelTrainRepository.findPolicyByPolicyId(activePolicyId);
                    if (policy == null) {
                        log.info(
                                "Skipping scheduler dataset because active policy detail not found. dataset_key={}, policy_id={}",
                                datasetKey,
                                activePolicyId
                        );
                        continue;
                    }

                    if (!isPolicyEnabledForAutoTrain(policy)) {
                        log.info(
                                "Skipping scheduler dataset because active policy is not executable. dataset_key={}, policy_id={}",
                                datasetKey,
                                activePolicyId
                        );
                        continue;
                    }

                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Model-train scheduler selected active policy candidate. dataset_key={}, active_policy_id={}, policy_use_flag={}, auto_train_enabled={}",
                                datasetKey,
                                activePolicyId,
                                normalizeOptionalText(policy.get("use_flag")),
                                asBoolean(policy.get("auto_train_enabled"), true)
                        );
                    }

                    if (!isPolicyDue(activeSelection, policy, datasetKey, activePolicyId, now)) {
                        continue;
                    }

                    executedCount++;
                    PolicyRunOutcome outcome = executePolicy(policy, TRIGGER_TYPE_SCHEDULE, false);
                    if (outcome.success()) {
                        successCount++;
                    }
                } catch (Exception exception) {
                    log.error("Model-train scheduler failed for one active dataset. Continuing next dataset.", exception);
                }
            }

            log.info("Model-train auto scheduler completed. executedPolicies={}, successPolicies={}", executedCount, successCount);
        } finally {
            autoRunLock.unlock();
        }
    }

    @Override
    public FeatureDatasetListResponseDto getFeatureDatasets() {
        List<Document> featurePolicies = modelTrainRepository.findActiveFeaturePolicies();
        List<FeatureDatasetDto> featureDatasets = new ArrayList<>();

        for (Document featurePolicy : featurePolicies) {
            String datasetKey = resolveOptionalDatasetKey(normalizeOptionalText(featurePolicy.get("dataset_key")));
            if (datasetKey == null || equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)) {
                continue;
            }

            Document datasetConfig = modelTrainRepository.findDatasetConfigByDatasetKey(datasetKey);
            FeatureConfigContext featureConfig = buildFeatureConfigContext(datasetKey, featurePolicy, datasetConfig, null);

            String datasetName = resolveFeatureDatasetName(featureConfig);
            String datasetLabel = schemaResolver.buildDatasetLabel(
                    datasetKey,
                    datasetName == null ? DEFAULT_DATASET_NAME : datasetName
            );
            String equipmentId = resolveEquipmentId(
                    datasetKey,
                    datasetConfig == null
                            ? null
                            : asString(datasetConfig.get("equipment_id"), normalizeOptionalText(datasetConfig.get(EQUIPMENT_FIELD)))
            );

            featureDatasets.add(new FeatureDatasetDto(
                    datasetKey,
                    datasetLabel,
                    datasetName,
                    equipmentId,
                    firstNonBlank(resolveFeatureSourceCollection(featureConfig), DEFAULT_SOURCE_COLLECTION),
                    firstNonBlank(resolveFeatureTargetCollection(featureConfig), "thisfeature"),
                    resolveFeatureSelectedColumns(featureConfig),
                    resolveFeatureWindowSizeOrNull(featureConfig),
                    firstNonBlank(resolveFeatureWindowMode(featureConfig), "fixed_count_only"),
                    resolveFeatureStats(featureConfig),
                    resolveFeatureSchedulerEnabled(featureConfig),
                    resolveFeatureSchedulerIntervalSec(featureConfig),
                    resolveFeatureLastStatus(featureConfig),
                    resolveFeatureLastWindowEnd(featureConfig),
                    resolveFeatureLastCheckpointValue(featureConfig),
                    featureConfig.configSource(),
                    featureConfig.configMessage(),
                    null,
                    null,
                    null
            ));
        }

        featureDatasets.sort(
                Comparator.comparing(FeatureDatasetDto::datasetLabel, Comparator.nullsLast(String::compareTo))
                        .thenComparing(FeatureDatasetDto::equipmentId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(FeatureDatasetDto::datasetKey, Comparator.nullsLast(String::compareTo))
        );

        return new FeatureDatasetListResponseDto(featureDatasets);
    }

    @Override
    public AnomalyResultListResponseDto getAnomalyResults(
            String runId,
            String datasetKey,
            String equipmentId,
            Integer limit
    ) {
        String normalizedRunId = normalizeRequiredText(runId, "run_id");
        String normalizedDatasetKey = resolveOptionalDatasetKey(datasetKey);
        String normalizedEquipmentId = resolveEquipmentSelection(normalizedDatasetKey, equipmentId);
        int resolvedLimit = resolveResultLimit(limit);

        Document runDocument = modelTrainRepository.findModelRunByRunId(normalizedRunId);
        if (runDocument == null) {
            throw new IllegalArgumentException("run_id not found.");
        }
        AnomalyRunDetailDto run = toAnomalyRunDetail(runDocument);
        validateAnomalyRunSelection(run, normalizedDatasetKey, normalizedEquipmentId);

        return new AnomalyResultListResponseDto(
                modelTrainRepository.findAnomalyResultsByIdentity(
                        normalizedRunId,
                        run.datasetKey(),
                        run.equipmentId(),
                        resolvedLimit
                )
        );
    }

    @Override
    public AnomalyRunListResponseDto getAnomalyRunOptions(
            String algoCode,
            String datasetKey,
            String equipmentId,
            Boolean includeNonSuccess,
            Integer limit
    ) {
        String normalizedAlgoCode = normalizeSupportedAnomalyAlgoCode(algoCode, false);
        String normalizedDatasetKey = resolveOptionalDatasetKey(datasetKey);
        String normalizedEquipmentId = resolveEquipmentSelection(normalizedDatasetKey, equipmentId);
        boolean shouldIncludeNonSuccess = includeNonSuccess == null || includeNonSuccess;
        int resolvedLimit = resolveRunListLimit(limit);

        List<String> targetAlgoCodes = normalizedAlgoCode == null
                ? supportedAlgoCodes()
                : List.of(normalizedAlgoCode);

        List<Document> runDocuments = modelTrainRepository.findRecentModelRuns(
                targetAlgoCodes,
                normalizedDatasetKey,
                normalizedEquipmentId,
                !shouldIncludeNonSuccess,
                resolvedLimit
        );
        List<AnomalyRunOptionDto> runs = runDocuments.stream()
                .map(this::toAnomalyRunOption)
                .filter(Objects::nonNull)
                .toList();

        String latestRunId = runs.isEmpty() ? null : runs.get(0).runId();
        String latestSuccessRunId = runs.stream()
                .filter(run -> SUCCESS_STATUS.equalsIgnoreCase(run.status()))
                .map(AnomalyRunOptionDto::runId)
                .findFirst()
                .orElse(null);

        return new AnomalyRunListResponseDto(
                supportedAlgoOptions(),
                runs,
                latestRunId,
                latestSuccessRunId
        );
    }

    @Override
    public AnomalyResultQueryResponseDto getAnomalyResultView(
            String algoCode,
            String runId,
            String datasetKey,
            String equipmentId,
            Integer limit
    ) {
        String normalizedDatasetKey = resolveOptionalDatasetKey(datasetKey);
        String normalizedEquipmentId = resolveEquipmentSelection(normalizedDatasetKey, equipmentId);
        String normalizedRunId = normalizeOptionalText(runId);
        int resolvedLimit = resolveResultLimit(limit);
        Document runDocument = resolveRunDocumentForAnomalyView(
                algoCode,
                runId,
                normalizedDatasetKey,
                normalizedEquipmentId
        );
        if (runDocument == null) {
            return emptyAnomalyResultQueryResponse(
                    normalizedDatasetKey,
                    normalizedEquipmentId,
                    normalizedRunId
            );
        }
        return buildAnomalyResultQueryResponse(
                runDocument,
                resolvedLimit,
                normalizedDatasetKey,
                normalizedEquipmentId
        );
    }

    @Override
    public AnomalyResultQueryResponseDto getAnomalyResultDetailByRunId(String runId, Integer limit) {
        String normalizedRunId = normalizeRequiredText(runId, "run_id");
        int resolvedLimit = resolveResultLimit(limit);
        Document runDocument = modelTrainRepository.findModelRunByRunId(normalizedRunId);
        if (runDocument == null) {
            throw new IllegalArgumentException("run_id not found.");
        }
        return buildAnomalyResultQueryResponse(runDocument, resolvedLimit, null, null);
    }

    @Override
    public AiOverviewResponseDto getAiOverview() {
        Date now = new Date();
        List<Document> activeSelections = modelTrainRepository.findEnabledModelActives();
        List<AiOverviewDatasetModelDto> activeModels = new ArrayList<>();
        for (Document activeSelection : activeSelections) {
            OverviewModelContext context = buildOverviewModelContext(activeSelection);
            if (context == null) {
                continue;
            }
            activeModels.add(toOverviewDatasetModel(context));
        }

        AiOverviewDatasetModelDto primaryModel = activeModels.isEmpty() ? null : activeModels.get(0);
        AiOverviewActiveModelDto activeModel = primaryModel == null
                ? new AiOverviewActiveModelDto(
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null
        )
                : new AiOverviewActiveModelDto(
                primaryModel.activeAlgoCode(),
                primaryModel.activeAlgoName(),
                primaryModel.activePolicyId(),
                primaryModel.datasetKey(),
                primaryModel.datasetLabel(),
                primaryModel.windowSize(),
                primaryModel.selectedColumnCount(),
                primaryModel.updatedAt()
        );
        AiOverviewLatestRunDto latestRunDto = primaryModel == null ? toOverviewLatestRun(null) : primaryModel.latestRun();
        AiOverviewAnomalySummaryDto anomalySummary = primaryModel == null ? emptyOverviewSummary() : primaryModel.summary();
        AiOverviewSupervisedSummaryDto supervisedSummary = primaryModel == null
                ? emptyOverviewSupervisedSummary()
                : primaryModel.supervisedSummary();
        AiOverviewFeatureSummaryDto featureSummary = primaryModel == null
                ? emptyOverviewFeatureSummary()
                : primaryModel.featureSummary();
        AiOverviewLabeledDataSummaryDto labeledDataSummary = primaryModel == null
                ? emptyOverviewLabeledDataSummary()
                : primaryModel.labeledDataSummary();

        return new AiOverviewResponseDto(
                List.copyOf(activeModels),
                activeModels.size(),
                activeModel,
                latestRunDto,
                anomalySummary,
                supervisedSummary,
                featureSummary,
                labeledDataSummary,
                now.toInstant().toString()
        );
    }

    private OverviewModelContext buildOverviewModelContext(Document activeSelection) {
        if (activeSelection == null) {
            return null;
        }

        String datasetKey = resolveOptionalDatasetKey(normalizeOptionalText(activeSelection.get("dataset_key")));
        String activePolicyId = normalizeOptionalText(activeSelection.get("active_policy_id"));
        Document activePolicy = activePolicyId == null ? null : modelTrainRepository.findPolicyByPolicyId(activePolicyId);
        if (datasetKey == null && activePolicy != null) {
            datasetKey = resolveOptionalDatasetKey(extractDatasetKeyFromPolicy(activePolicy));
        }

        String rawAlgoCode = activePolicy == null
                ? null
                : normalizeOptionalText(activePolicy.get("algo_code"));
        if (rawAlgoCode == null) {
            rawAlgoCode = normalizeOptionalText(activeSelection.get("active_algo_code"));
        }
        String normalizedAlgoCode = normalizeSupportedAlgoCode(rawAlgoCode, false);
        String activeAlgoCode = normalizedAlgoCode != null
                ? normalizedAlgoCode
                : rawAlgoCode == null ? null : rawAlgoCode.toUpperCase(Locale.ROOT);

        String activeAlgoName = activePolicy == null ? null : normalizeOptionalText(activePolicy.get("algo_name"));
        if (activeAlgoName == null) {
            activeAlgoName = normalizeOptionalText(activeSelection.get("active_algo_name"));
        }
        if (activeAlgoName == null && activeAlgoCode != null) {
            activeAlgoName = defaultAlgoName(activeAlgoCode);
        }

        String regDate = normalizeTimestamp(activeSelection.get("reg_date"));
        String updatedAt = normalizeTimestamp(activeSelection.get("updated_at"));
        if (updatedAt == null) {
            updatedAt = regDate;
        }

        FeatureConfigContext featureConfig = resolveFeatureConfigContext(datasetKey, "AI_OVERVIEW");
        List<String> selectedColumns = resolveFeatureSelectedColumns(featureConfig);
        Integer windowSize = resolveFeatureWindowSizeOrNull(featureConfig);
        String sourceCollection = resolveOverviewSourceCollection(datasetKey, featureConfig);
        String datasetName = resolveFeatureDatasetName(featureConfig);
        String datasetLabel = datasetKey == null
                ? null
                : schemaResolver.buildDatasetLabel(
                datasetKey,
                datasetName == null ? (sourceCollection == null ? DEFAULT_DATASET_NAME : sourceCollection) : datasetName
        );

        Document latestRun = resolveLatestRunForOverview(activePolicyId, datasetKey, activeAlgoCode);
        AiOverviewLatestRunDto latestRunDto = toOverviewLatestRun(latestRun);
        String summaryType = resolveOverviewSummaryType(activeAlgoCode, latestRun);
        boolean isSupervisedSummary = OVERVIEW_SUMMARY_TYPE_SUPERVISED.equals(summaryType);

        AiOverviewAnomalySummaryDto summary = isSupervisedSummary
                ? emptyOverviewSummary()
                : buildOverviewAnomalySummary(latestRun);

        AiOverviewSupervisedSummaryDto supervisedSummary = isSupervisedSummary
                ? buildOverviewSupervisedSummary(activeAlgoCode, datasetKey, latestRun)
                : emptyOverviewSupervisedSummary();

        AiOverviewFeatureSummaryDto featureSummary = isSupervisedSummary
                ? emptyOverviewFeatureSummary()
                : buildOverviewFeatureSummary(
                datasetKey,
                datasetLabel,
                featureConfig,
                selectedColumns,
                windowSize,
                latestRun
        );

        AiOverviewLabeledDataSummaryDto labeledDataSummary = isSupervisedSummary
                ? buildOverviewLabeledDataSummary(datasetKey, latestRun, supervisedSummary)
                : emptyOverviewLabeledDataSummary();

        return new OverviewModelContext(
                datasetKey,
                datasetLabel,
                sourceCollection,
                activePolicyId,
                activeAlgoCode,
                activeAlgoName,
                summaryType,
                windowSize,
                selectedColumns.size(),
                regDate,
                updatedAt,
                latestRunDto,
                summary,
                supervisedSummary,
                featureSummary,
                labeledDataSummary
        );
    }

    private AiOverviewDatasetModelDto toOverviewDatasetModel(OverviewModelContext context) {
        return new AiOverviewDatasetModelDto(
                context.datasetKey(),
                context.datasetLabel(),
                context.sourceCollection(),
                context.activePolicyId(),
                context.activeAlgoCode(),
                context.activeAlgoName(),
                context.summaryType(),
                context.windowSize(),
                context.selectedColumnCount(),
                context.regDate(),
                context.updatedAt(),
                context.latestRun(),
                context.summary(),
                context.supervisedSummary(),
                context.featureSummary(),
                context.labeledDataSummary()
        );
    }

    private String resolveOverviewSourceCollection(String datasetKey, FeatureConfigContext featureConfig) {
        String sourceCollection = resolveFeatureSourceCollection(featureConfig);
        if (sourceCollection != null) {
            return sourceCollection;
        }

        Document datasetConfig = featureConfig == null ? null : featureConfig.datasetConfig();
        String datasetName = datasetConfig == null ? null : normalizeOptionalText(datasetConfig.get("dataset_name"));
        if (datasetName != null) {
            return datasetName;
        }

        String normalizedDatasetKey = resolveOptionalDatasetKey(datasetKey);
        if (normalizedDatasetKey == null) {
            return DEFAULT_SOURCE_COLLECTION;
        }
        String[] tokens = normalizedDatasetKey.split("_");
        if (tokens.length >= 1 && !tokens[0].isBlank()) {
            if (tokens.length >= 2) {
                return (tokens[0] + "_" + tokens[1]).toUpperCase(Locale.ROOT);
            }
            return tokens[0].toUpperCase(Locale.ROOT);
        }
        return DEFAULT_SOURCE_COLLECTION;
    }

    @Override
    public HomeInsightResponseDto getHomeInsight(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        Document targetRun = null;
        if (normalizedRunId != null) {
            targetRun = modelTrainRepository.findModelRunByRunId(normalizedRunId);
            if (targetRun == null) {
                throw new IllegalArgumentException("run_id not found.");
            }
        }
        if (targetRun == null) {
            Document activeSelection = selectPrimaryActiveSelection(modelTrainRepository.findEnabledModelActives());
            String datasetKey = resolveOptionalDatasetKey(
                    activeSelection == null ? null : normalizeOptionalText(activeSelection.get("dataset_key"))
            );
            String activePolicyId = activeSelection == null ? null : normalizeOptionalText(activeSelection.get("active_policy_id"));
            Document activePolicy = activePolicyId == null ? null : modelTrainRepository.findPolicyByPolicyId(activePolicyId);
            if (datasetKey == null && activePolicy != null) {
                datasetKey = resolveOptionalDatasetKey(extractDatasetKeyFromPolicy(activePolicy));
            }
            targetRun = resolveLatestRunForOverview(activePolicyId, datasetKey, null);
        }

        if (targetRun == null) {
            return new HomeInsightResponseDto(
                    null,
                    buildHomeTrendTemplate(),
                    List.of(),
                    List.of()
            );
        }
        if (!isAnomalyRunDocument(targetRun)) {
            String datasetKey = resolveOptionalDatasetKey(normalizeOptionalText(targetRun.get("dataset_key")));
            String equipmentId = asString(targetRun.get("equipment_id"), normalizeOptionalText(targetRun.get(EQUIPMENT_FIELD)));
            targetRun = resolveLatestAnomalyRunByDatasetAndEquipment(datasetKey, equipmentId);
            if (targetRun == null) {
                return new HomeInsightResponseDto(
                        null,
                        buildHomeTrendTemplate(),
                        List.of(),
                        List.of()
                );
            }
        }

        AnomalyRunDetailDto run = toAnomalyRunDetail(targetRun);
        Instant fromInstant = Instant.now().minus(HOME_LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<Map<String, Object>> rawRows = modelTrainRepository.findAnomalyResultsByRunIdAfterWindowEnd(
                run.runId(),
                Date.from(fromInstant)
        );
        List<AnomalyResultPointDto> points = rawRows.stream()
                .map(rawRow -> toAnomalyResultPoint(run, rawRow))
                .toList();
        List<AnomalyResultPointDto> calibratedPoints = applyAlgorithmHealthCalibration(run, points);

        return new HomeInsightResponseDto(
                run.runId(),
                buildHomeTrendPoints(calibratedPoints),
                buildHomeTopSensors(calibratedPoints),
                buildHomeRecentWindows(calibratedPoints)
        );
    }

    private Document resolveLatestRunForOverview(String activePolicyId, String datasetKey, String algoCode) {
        Document latestRun = modelTrainRepository.findLatestModelRunByDatasetPolicyAndAlgo(datasetKey, activePolicyId, algoCode);
        if (latestRun == null && activePolicyId != null) {
            latestRun = modelTrainRepository.findLatestModelRunByPolicyId(activePolicyId);
        }
        if (latestRun == null && datasetKey != null && algoCode != null) {
            latestRun = modelTrainRepository.findLatestModelRunByDatasetPolicyAndAlgo(datasetKey, null, algoCode);
        }
        if (latestRun == null && datasetKey != null) {
            latestRun = modelTrainRepository.findLatestModelRunByDatasetKey(datasetKey);
        }
        return latestRun;
    }

    private String resolveOverviewSummaryType(String algoCode, Document latestRun) {
        String normalizedAlgoCode = normalizeSupportedAlgoCode(algoCode, false);
        if (normalizedAlgoCode == null && latestRun != null) {
            normalizedAlgoCode = normalizeSupportedAlgoCode(normalizeOptionalText(latestRun.get("algo_code")), false);
        }
        if (ALGO_RANDOM_FOREST.equals(normalizedAlgoCode)) {
            return OVERVIEW_SUMMARY_TYPE_SUPERVISED;
        }
        if (ALGO_ISOLATION_FOREST.equals(normalizedAlgoCode) || ALGO_AUTOENCODER.equals(normalizedAlgoCode)) {
            return OVERVIEW_SUMMARY_TYPE_UNSUPERVISED;
        }
        return OVERVIEW_SUMMARY_TYPE_UNSUPERVISED;
    }

    private AiOverviewSupervisedSummaryDto buildOverviewSupervisedSummary(
            String algoCode,
            String datasetKey,
            Document latestRun
    ) {
        String normalizedAlgoCode = normalizeSupportedAlgoCode(algoCode, false);
        if (normalizedAlgoCode == null && latestRun != null) {
            normalizedAlgoCode = normalizeSupportedAlgoCode(normalizeOptionalText(latestRun.get("algo_code")), false);
        }
        if (!ALGO_RANDOM_FOREST.equals(normalizedAlgoCode)) {
            return emptyOverviewSupervisedSummary();
        }

        String latestRunId = latestRun == null ? null : normalizeOptionalText(latestRun.get("run_id"));
        if (latestRunId == null) {
            return emptyOverviewSupervisedSummary();
        }

        Document latestModelEval = modelTrainRepository.findLatestModelEvalByRunId(latestRunId);
        long classificationResultCount = Math.max(modelTrainRepository.countClassificationResultsByRunId(latestRunId), 0L);
        long correctCount = Math.max(modelTrainRepository.countClassificationByCorrectYn(latestRunId, CORRECT_YN_Y), 0L);
        long misclassifiedCount = Math.max(modelTrainRepository.countClassificationByCorrectYn(latestRunId, CORRECT_YN_N), 0L);
        Map<String, Long> confusionCounts = modelTrainRepository.aggregateClassificationErrorTypeCountsByRunId(latestRunId);

        long tp = resolveOverviewCountFromEval(latestModelEval, "tp", confusionCounts.getOrDefault("TP", 0L));
        long tn = resolveOverviewCountFromEval(latestModelEval, "tn", confusionCounts.getOrDefault("TN", 0L));
        long fp = resolveOverviewCountFromEval(latestModelEval, "fp", confusionCounts.getOrDefault("FP", 0L));
        long fn = resolveOverviewCountFromEval(latestModelEval, "fn", confusionCounts.getOrDefault("FN", 0L));

        long matrixTotal = Math.max(tp + tn + fp + fn, 0L);
        long testCount = resolveOverviewCountFromEval(
                latestModelEval,
                "test_count",
                classificationResultCount > 0L ? classificationResultCount : matrixTotal
        );
        if (testCount <= 0L) {
            testCount = classificationResultCount > 0L ? classificationResultCount : matrixTotal;
        }

        long trainCount = resolveOverviewCountFromEval(latestModelEval, "train_count", 0L);
        long totalCount = resolveOverviewCountFromEval(latestModelEval, "total_count", trainCount + testCount);
        if (totalCount <= 0L) {
            totalCount = Math.max(trainCount + testCount, matrixTotal);
        }
        long excludedUnknownCount = resolveOverviewCountFromEval(latestModelEval, "excluded_unknown_count", 0L);
        long normalCount = resolveOverviewCountFromEval(latestModelEval, "normal_count", 0L);
        long anomalyCount = resolveOverviewCountFromEval(latestModelEval, "anomaly_count", 0L);

        if (correctCount <= 0L && matrixTotal > 0L) {
            correctCount = tp + tn;
        }
        if (misclassifiedCount <= 0L && matrixTotal > 0L) {
            misclassifiedCount = fp + fn;
        }

        Double accuracy = resolveOverviewMetricFromEval(latestModelEval, "accuracy", tp + tn, testCount);
        Double precision = resolveOverviewMetricFromEval(latestModelEval, "precision", tp, tp + fp);
        Double recall = resolveOverviewMetricFromEval(latestModelEval, "recall", tp, tp + fn);
        Double f1Score = resolveOverviewMetricFromEval(latestModelEval, "f1_score", 2L * tp, (2L * tp) + fp + fn);

        String latestEvalExecutedAt = latestModelEval == null
                ? null
                : firstNonBlank(
                normalizeTimestamp(latestModelEval.get("updated_at")),
                normalizeTimestamp(latestModelEval.get("reg_date"))
        );

        return new AiOverviewSupervisedSummaryDto(
                accuracy,
                precision,
                recall,
                f1Score,
                Math.max(testCount, 0L),
                Math.max(trainCount, 0L),
                Math.max(totalCount, 0L),
                Math.max(excludedUnknownCount, 0L),
                Math.max(normalCount, 0L),
                Math.max(anomalyCount, 0L),
                Math.max(tp, 0L),
                Math.max(tn, 0L),
                Math.max(fp, 0L),
                Math.max(fn, 0L),
                Math.max(correctCount, 0L),
                Math.max(misclassifiedCount, 0L),
                Math.max(classificationResultCount, 0L),
                latestEvalExecutedAt,
                buildOverviewTopFeatureImportances(latestModelEval)
        );
    }

    private List<AiOverviewFeatureImportanceDto> buildOverviewTopFeatureImportances(Document latestModelEval) {
        if (latestModelEval == null) {
            return List.of();
        }
        Object rawFeatureImportances = latestModelEval.get("feature_importances");
        if (!(rawFeatureImportances instanceof List<?> rawFeatureImportanceList)) {
            return List.of();
        }

        List<AiOverviewFeatureImportanceDto> items = new ArrayList<>();
        for (Object rawFeatureImportance : rawFeatureImportanceList) {
            if (!(rawFeatureImportance instanceof Map<?, ?> row)) {
                continue;
            }
            String feature = normalizeOptionalText(row.get("feature"));
            if (feature == null) {
                continue;
            }
            Double importance = toDouble(row.get("importance"));
            if (importance == null || !Double.isFinite(importance)) {
                importance = 0D;
            }
            items.add(new AiOverviewFeatureImportanceDto(0, feature, importance));
        }

        if (items.isEmpty()) {
            return List.of();
        }

        items.sort((left, right) -> {
            int byImportance = Double.compare(
                    right.importance() == null ? 0D : right.importance(),
                    left.importance() == null ? 0D : left.importance()
            );
            if (byImportance != 0) {
                return byImportance;
            }
            return left.feature().compareTo(right.feature());
        });

        List<AiOverviewFeatureImportanceDto> topItems = new ArrayList<>();
        int limit = Math.min(items.size(), OVERVIEW_FEATURE_IMPORTANCE_LIMIT);
        for (int index = 0; index < limit; index++) {
            AiOverviewFeatureImportanceDto item = items.get(index);
            topItems.add(new AiOverviewFeatureImportanceDto(index + 1, item.feature(), item.importance()));
        }
        return List.copyOf(topItems);
    }

    private AiOverviewLabeledDataSummaryDto buildOverviewLabeledDataSummary(
            String datasetKey,
            Document latestRun,
            AiOverviewSupervisedSummaryDto supervisedSummary
    ) {
        String targetDatasetKey = datasetKey;
        if (targetDatasetKey == null && latestRun != null) {
            targetDatasetKey = resolveOptionalDatasetKey(normalizeOptionalText(latestRun.get("dataset_key")));
        }

        String latestRunId = latestRun == null ? null : normalizeOptionalText(latestRun.get("run_id"));
        Document latestModelEval = latestRunId == null ? null : modelTrainRepository.findLatestModelEvalByRunId(latestRunId);

        String labelVersion = latestModelEval == null ? null : normalizeOptionalText(latestModelEval.get("label_version"));
        if (labelVersion == null && latestRun != null && latestRun.get("params") instanceof Map<?, ?> params) {
            labelVersion = normalizeOptionalText(params.get(PARAM_LABEL_VERSION));
        }

        if (targetDatasetKey == null) {
            return new AiOverviewLabeledDataSummaryDto(
                    LABELED_DATA_NOT_READY_LABEL,
                    null,
                    labelVersion,
                    supervisedSummary == null ? 0L : Math.max(supervisedSummary.totalCount(), 0L),
                    supervisedSummary == null ? 0L : Math.max(supervisedSummary.trainCount(), 0L),
                    supervisedSummary == null ? 0L : Math.max(supervisedSummary.testCount(), 0L),
                    supervisedSummary == null ? 0L : Math.max(supervisedSummary.excludedUnknownCount(), 0L),
                    supervisedSummary == null ? 0L : Math.max(supervisedSummary.normalCount(), 0L),
                    supervisedSummary == null ? 0L : Math.max(supervisedSummary.anomalyCount(), 0L)
            );
        }

        long rawTotalCount = modelTrainRepository.countLabeledRowsByDatasetKey(targetDatasetKey);
        long rawNormalCount = modelTrainRepository.countLabeledRowsByLabel(targetDatasetKey, 0);
        long rawAnomalyCount = modelTrainRepository.countLabeledRowsByLabel(targetDatasetKey, 1);

        long totalCount = supervisedSummary == null ? 0L : supervisedSummary.totalCount();
        if (totalCount <= 0L) {
            totalCount = rawTotalCount;
        }

        long trainCount = supervisedSummary == null ? 0L : supervisedSummary.trainCount();
        long testCount = supervisedSummary == null ? 0L : supervisedSummary.testCount();
        long excludedUnknownCount = supervisedSummary == null ? 0L : supervisedSummary.excludedUnknownCount();
        long normalCount = supervisedSummary == null ? 0L : supervisedSummary.normalCount();
        long anomalyCount = supervisedSummary == null ? 0L : supervisedSummary.anomalyCount();

        if (normalCount <= 0L) {
            normalCount = rawNormalCount;
        }
        if (anomalyCount <= 0L) {
            anomalyCount = rawAnomalyCount;
        }

        return new AiOverviewLabeledDataSummaryDto(
                LABELED_SOURCE_COLLECTION,
                targetDatasetKey,
                labelVersion,
                Math.max(totalCount, 0L),
                Math.max(trainCount, 0L),
                Math.max(testCount, 0L),
                Math.max(excludedUnknownCount, 0L),
                Math.max(normalCount, 0L),
                Math.max(anomalyCount, 0L)
        );
    }

    private boolean isAnomalyRunDocument(Document runDocument) {
        if (runDocument == null) {
            return false;
        }
        String algoCode = normalizeOptionalText(runDocument.get("algo_code"));
        String normalizedAlgoCode = normalizeSupportedAnomalyAlgoCode(algoCode, false);
        return normalizedAlgoCode != null;
    }

    private Document resolveLatestAnomalyRunByDatasetAndEquipment(String datasetKey, String equipmentId) {
        Document latestRun = null;
        for (String anomalyAlgoCode : supportedAlgoCodes()) {
            Document candidate = modelTrainRepository.findLatestModelRunByDatasetAndAlgo(datasetKey, equipmentId, anomalyAlgoCode);
            if (candidate == null && equipmentId != null) {
                candidate = modelTrainRepository.findLatestModelRunByDatasetAndAlgo(datasetKey, null, anomalyAlgoCode);
            }
            if (candidate == null) {
                continue;
            }
            Instant candidateAt = parseInstantOrNull(candidate.get("reg_date"));
            Instant latestAt = latestRun == null ? null : parseInstantOrNull(latestRun.get("reg_date"));
            if (latestRun == null || (candidateAt != null && (latestAt == null || candidateAt.isAfter(latestAt)))) {
                latestRun = candidate;
            }
        }
        return latestRun;
    }

    private Document selectPrimaryActiveSelection(List<Document> activeSelections) {
        if (activeSelections == null || activeSelections.isEmpty()) {
            return null;
        }

        Document selected = null;
        Instant selectedAt = null;
        for (Document activeSelection : activeSelections) {
            Instant candidateAt = parseInstantOrNull(activeSelection.get("updated_at"));
            if (candidateAt == null) {
                candidateAt = parseInstantOrNull(activeSelection.get("reg_date"));
            }
            if (selected == null || (candidateAt != null && (selectedAt == null || candidateAt.isAfter(selectedAt)))) {
                selected = activeSelection;
                selectedAt = candidateAt;
            }
        }
        return selected == null ? activeSelections.get(0) : selected;
    }

    private List<HomeInsightTrendPointDto> buildHomeTrendTemplate() {
        LinkedHashMap<String, Long> template = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(HOME_ZONE_OFFSET);
        for (int offset = HOME_LOOKBACK_DAYS - 1; offset >= 0; offset--) {
            String dayKey = today.minusDays(offset).format(HOME_DAY_FORMATTER);
            template.put(dayKey, 0L);
        }

        return template.entrySet().stream()
                .map(entry -> new HomeInsightTrendPointDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<HomeInsightTrendPointDto> buildHomeTrendPoints(List<AnomalyResultPointDto> points) {
        LinkedHashMap<String, Long> dayCounts = new LinkedHashMap<>();
        for (HomeInsightTrendPointDto templatePoint : buildHomeTrendTemplate()) {
            dayCounts.put(templatePoint.date(), 0L);
        }

        for (AnomalyResultPointDto point : points) {
            if (!isHomeAlertPoint(point)) {
                continue;
            }
            Instant pointInstant = resolvePointInstant(point);
            if (pointInstant == null) {
                continue;
            }
            String dayKey = pointInstant.atOffset(HOME_ZONE_OFFSET).toLocalDate().format(HOME_DAY_FORMATTER);
            if (!dayCounts.containsKey(dayKey)) {
                continue;
            }
            dayCounts.merge(dayKey, 1L, Long::sum);
        }

        return dayCounts.entrySet().stream()
                .map(entry -> new HomeInsightTrendPointDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<HomeInsightTopSensorDto> buildHomeTopSensors(List<AnomalyResultPointDto> points) {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (AnomalyResultPointDto point : points) {
            if (!isHomeAlertPoint(point)) {
                continue;
            }
            List<String> topCauseKeys = extractTopCauseKeys(point.inputFeatures(), HOME_TOP_CAUSE_LIMIT);
            for (String key : topCauseKeys) {
                counts.merge(key, 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted(
                        Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                                .thenComparing(Map.Entry.comparingByKey())
                )
                .limit(HOME_TOP_SENSOR_LIMIT)
                .map(entry -> new HomeInsightTopSensorDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<HomeInsightRecentWindowDto> buildHomeRecentWindows(List<AnomalyResultPointDto> points) {
        List<HomeInsightRecentWindowDto> recentWindows = new ArrayList<>();
        int fallbackIndex = 0;

        for (AnomalyResultPointDto point : points) {
            if (!isHomeAlertPoint(point)) {
                continue;
            }
            if (recentWindows.size() >= HOME_RECENT_WINDOW_LIMIT) {
                break;
            }

            Double anomalyScore = point.anomalyScore();
            if (anomalyScore != null && !Double.isFinite(anomalyScore)) {
                anomalyScore = null;
            }

            recentWindows.add(new HomeInsightRecentWindowDto(
                    buildHomeRecentWindowId(point, fallbackIndex),
                    point.windowStart(),
                    point.windowEnd(),
                    normalizeHomeStatus(point.status()),
                    anomalyScore,
                    extractTopCauseKeys(point.inputFeatures(), HOME_TOP_CAUSE_LIMIT)
            ));
            fallbackIndex++;
        }

        return List.copyOf(recentWindows);
    }

    private String buildHomeRecentWindowId(AnomalyResultPointDto point, int fallbackIndex) {
        String runId = normalizeOptionalText(point.runId());
        String windowStart = normalizeOptionalText(point.windowStart());
        if (runId == null) {
            runId = "RUN";
        }
        if (windowStart == null) {
            windowStart = "WINDOW";
        }
        return runId + "-" + windowStart + "-" + fallbackIndex;
    }

    private String normalizeHomeStatus(String status) {
        String normalized = normalizeOptionalText(status);
        if (normalized == null) {
            return NO_DATA_STATUS;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private Instant resolvePointInstant(AnomalyResultPointDto point) {
        Instant pointInstant = parseInstantOrNull(point.windowEnd());
        if (pointInstant == null) {
            pointInstant = parseInstantOrNull(point.regDate());
        }
        if (pointInstant == null) {
            pointInstant = parseInstantOrNull(point.windowStart());
        }
        return pointInstant;
    }

    private boolean isHomeAlertPoint(AnomalyResultPointDto point) {
        String status = normalizeOptionalText(point.status());
        if (status != null) {
            String normalizedStatus = status.toUpperCase(Locale.ROOT);
            if (WARNING_STATUS.equals(normalizedStatus) || CRITICAL_STATUS.equals(normalizedStatus)) {
                return true;
            }
        }
        return Boolean.TRUE.equals(point.isAnomaly());
    }

    private List<String> extractTopCauseKeys(Map<String, Object> inputFeatures, int limit) {
        if (inputFeatures == null || inputFeatures.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<Map.Entry<String, Double>> numericEntries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : inputFeatures.entrySet()) {
            String key = normalizeOptionalText(entry.getKey());
            if (key == null) {
                continue;
            }
            Double value = toDouble(entry.getValue());
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            numericEntries.add(Map.entry(key, value));
        }

        numericEntries.sort((left, right) ->
                Double.compare(Math.abs(right.getValue()), Math.abs(left.getValue()))
        );

        List<String> topCauseKeys = new ArrayList<>();
        for (int index = 0; index < numericEntries.size() && index < limit; index++) {
            topCauseKeys.add(numericEntries.get(index).getKey());
        }
        return List.copyOf(topCauseKeys);
    }

    private AiOverviewLatestRunDto toOverviewLatestRun(Document runDocument) {
        if (runDocument == null) {
            return new AiOverviewLatestRunDto(
                    null,
                    NO_DATA_STATUS,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "No recent run history."
            );
        }

        String status = asString(runDocument.get("status"), NO_DATA_STATUS).toUpperCase(Locale.ROOT);
        String triggerType = normalizeOptionalText(runDocument.get("trigger_type"));
        String startedAt = normalizeTimestamp(runDocument.get("reg_date"));
        String endedAt = normalizeTimestamp(runDocument.get("updated_at"));
        String executedAt = endedAt == null ? startedAt : endedAt;

        String rawAlgoCode = normalizeOptionalText(runDocument.get("algo_code"));
        String normalizedAlgoCode = normalizeSupportedAlgoCode(rawAlgoCode, false);
        String algoCode = normalizedAlgoCode != null
                ? normalizedAlgoCode
                : rawAlgoCode == null ? null : rawAlgoCode.toUpperCase(Locale.ROOT);
        String algoName = asString(runDocument.get("algo_name"), algoCode == null ? null : defaultAlgoName(algoCode));
        String datasetKey = resolveOptionalDatasetKey(normalizeOptionalText(runDocument.get("dataset_key")));

        return new AiOverviewLatestRunDto(
                normalizeOptionalText(runDocument.get("run_id")),
                status,
                triggerType,
                startedAt,
                endedAt,
                executedAt,
                algoCode,
                algoName,
                datasetKey,
                buildOverviewRunMessage(status)
        );
    }

    private String buildOverviewRunMessage(String status) {
        if (status == null) {
            return "Model execution status is unknown";
        }

        return switch (status.toUpperCase(Locale.ROOT)) {
            case SUCCESS_STATUS -> "Model execution completed";
            case FAIL_STATUS -> "Model execution failed. Check model server logs.";
            case RUNNING_STATUS -> "Model execution is running";
            case SKIPPED_STATUS -> "Model execution skipped";
            default -> "Model execution status is unknown";
        };
    }

    private AiOverviewAnomalySummaryDto buildOverviewAnomalySummary(Document latestRun) {
        String latestRunId = latestRun == null ? null : normalizeOptionalText(latestRun.get("run_id"));
        Map<String, Long> statusCounts = buildEmptyOverviewStatusCounts();

        if (latestRunId == null) {
            return new AiOverviewAnomalySummaryDto(
                    null,
                    null,
                    0L,
                    0L,
                    statusCounts
            );
        }

        Document summary = modelTrainRepository.aggregateAnomalySummaryByRunId(latestRunId);
        Map<String, Long> dynamicStatusCounts = modelTrainRepository.aggregateAnomalyStatusCountsByRunId(latestRunId);
        for (Map.Entry<String, Long> entry : dynamicStatusCounts.entrySet()) {
            String key = normalizeOptionalText(entry.getKey());
            if (key == null) {
                continue;
            }
            String normalizedKey = key.toUpperCase(Locale.ROOT);
            statusCounts.merge(normalizedKey, entry.getValue() == null ? 0L : entry.getValue(), Long::sum);
        }

        return new AiOverviewAnomalySummaryDto(
                summary == null ? null : toDouble(summary.get("avg_anomaly_score")),
                summary == null ? null : toDouble(summary.get("avg_health_index")),
                summary == null ? 0L : asLong(summary.get("anomaly_count")),
                summary == null ? 0L : asLong(summary.get("total_count")),
                statusCounts
        );
    }

    private AiOverviewAnomalySummaryDto emptyOverviewSummary() {
        return new AiOverviewAnomalySummaryDto(
                null,
                null,
                0L,
                0L,
                buildEmptyOverviewStatusCounts()
        );
    }

    private AiOverviewSupervisedSummaryDto emptyOverviewSupervisedSummary() {
        return new AiOverviewSupervisedSummaryDto(
                null,
                null,
                null,
                null,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                null,
                List.of()
        );
    }

    private AiOverviewLabeledDataSummaryDto emptyOverviewLabeledDataSummary() {
        return new AiOverviewLabeledDataSummaryDto(
                LABELED_DATA_NOT_READY_LABEL,
                null,
                null,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L
        );
    }

    private long resolveOverviewCountFromEval(Document latestModelEval, String fieldName, long fallback) {
        if (latestModelEval != null && fieldName != null && latestModelEval.containsKey(fieldName)) {
            return Math.max(asLong(latestModelEval.get(fieldName)), 0L);
        }
        return Math.max(fallback, 0L);
    }

    private Double resolveOverviewMetricFromEval(
            Document latestModelEval,
            String fieldName,
            long numerator,
            long denominator
    ) {
        Double metric = latestModelEval == null ? null : toDouble(latestModelEval.get(fieldName));
        if (metric != null && Double.isFinite(metric)) {
            return metric;
        }
        if (denominator <= 0L) {
            return null;
        }
        return Math.max(numerator, 0L) / (double) denominator;
    }

    private Map<String, Long> buildEmptyOverviewStatusCounts() {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (String status : OVERVIEW_STATUS_ORDER) {
            statusCounts.put(status, 0L);
        }
        return statusCounts;
    }

    private AiOverviewFeatureSummaryDto emptyOverviewFeatureSummary() {
        return new AiOverviewFeatureSummaryDto(
                null,
                null,
                null,
                0L,
                null,
                null,
                0
        );
    }

    private AiOverviewFeatureSummaryDto buildOverviewFeatureSummary(
            String datasetKey,
            String datasetLabel,
            FeatureConfigContext featureConfig,
            List<String> selectedColumns,
            Integer windowSize,
            Document latestRun
    ) {
        String targetDatasetKey = datasetKey;
        if (targetDatasetKey == null && latestRun != null) {
            targetDatasetKey = resolveOptionalDatasetKey(normalizeOptionalText(latestRun.get("dataset_key")));
        }

        if (targetDatasetKey == null) {
            return new AiOverviewFeatureSummaryDto(
                    null,
                    null,
                    null,
                    0L,
                    null,
                    null,
                    0
            );
        }

        FeatureConfigContext targetFeatureConfig = featureConfig;
        if (targetFeatureConfig == null || !targetDatasetKey.equalsIgnoreCase(targetFeatureConfig.datasetKey())) {
            targetFeatureConfig = resolveFeatureConfigContext(targetDatasetKey, "AI_OVERVIEW_FEATURE_SUMMARY");
        }
        Document targetDatasetConfig = targetFeatureConfig == null ? null : targetFeatureConfig.datasetConfig();
        if (targetDatasetConfig == null) {
            targetDatasetConfig = modelTrainRepository.findDatasetConfigByDatasetKey(targetDatasetKey);
        }

        List<String> targetColumns = selectedColumns == null || selectedColumns.isEmpty()
                ? resolveFeatureSelectedColumns(targetFeatureConfig)
                : selectedColumns;
        int selectedColumnCount = targetColumns == null ? 0 : targetColumns.size();

        Integer targetWindowSize = windowSize;
        if (targetWindowSize == null) {
            targetWindowSize = resolveFeatureWindowSizeOrNull(targetFeatureConfig);
        }

        String targetSourceDatasetName = resolveFeatureDatasetName(targetFeatureConfig);
        if (targetSourceDatasetName == null && targetDatasetConfig != null) {
            targetSourceDatasetName = asString(
                    targetDatasetConfig.get("dataset_name"),
                    asString(targetDatasetConfig.get("source_collection"), DEFAULT_DATASET_NAME)
            );
        }
        if (targetSourceDatasetName == null) {
            targetSourceDatasetName = DEFAULT_DATASET_NAME;
        }
        String targetDatasetLabel = datasetLabel != null
                ? datasetLabel
                : schemaResolver.buildDatasetLabel(targetDatasetKey, targetSourceDatasetName);

        long totalFeatureCount = modelTrainRepository.countFeatureRowsByDatasetKey(targetDatasetKey);
        List<Document> latestFeatureRows = modelTrainRepository.findLatestFeatureRowsByDatasetKey(targetDatasetKey, 1);
        String latestFeatureCreatedAt = null;
        if (!latestFeatureRows.isEmpty()) {
            Document latestFeature = latestFeatureRows.get(latestFeatureRows.size() - 1);
            latestFeatureCreatedAt = normalizeTimestamp(latestFeature.get("reg_date"));
            if (latestFeatureCreatedAt == null) {
                latestFeatureCreatedAt = normalizeTimestamp(latestFeature.get("updated_at"));
            }
            if (latestFeatureCreatedAt == null) {
                latestFeatureCreatedAt = normalizeTimestamp(latestFeature.get("window_end"));
            }
        }

        return new AiOverviewFeatureSummaryDto(
                targetDatasetKey,
                targetDatasetLabel,
                targetSourceDatasetName,
                totalFeatureCount,
                latestFeatureCreatedAt,
                targetWindowSize,
                selectedColumnCount
        );
    }

    private Document resolveRunDocumentForAnomalyView(
            String algoCode,
            String runId,
            String datasetKey,
            String equipmentId
    ) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId != null) {
            Document runDocument = modelTrainRepository.findModelRunByRunId(normalizedRunId);
            if (runDocument == null) {
                throw new IllegalArgumentException("run_id not found.");
            }
            AnomalyRunDetailDto selectedRun = toAnomalyRunDetail(runDocument);
            validateAnomalyRunSelection(selectedRun, datasetKey, equipmentId);

            String normalizedAlgoCode = normalizeSupportedAnomalyAlgoCode(algoCode, false);
            if (normalizedAlgoCode != null && !normalizedAlgoCode.equalsIgnoreCase(selectedRun.algoCode())) {
                throw new IllegalArgumentException(
                        "Selected run_id does not match the selected algorithm. run_id="
                                + selectedRun.runId()
                                + ", algo_code="
                                + selectedRun.algoCode()
                                + ", selected_algo_code="
                                + normalizedAlgoCode
                );
            }
            return runDocument;
        }

        String normalizedAlgoCode = normalizeSupportedAnomalyAlgoCode(algoCode, false);
        List<String> targetAlgoCodes = normalizedAlgoCode == null
                ? supportedAlgoCodes()
                : List.of(normalizedAlgoCode);
        List<Document> latestRuns = modelTrainRepository.findRecentModelRuns(
                targetAlgoCodes,
                datasetKey,
                equipmentId,
                true,
                1
        );
        if (!latestRuns.isEmpty()) {
            return latestRuns.get(0);
        }
        latestRuns = modelTrainRepository.findRecentModelRuns(
                targetAlgoCodes,
                datasetKey,
                equipmentId,
                false,
                1
        );
        if (latestRuns.isEmpty()) {
            return null;
        }
        return latestRuns.get(0);
    }

    private AnomalyResultQueryResponseDto buildAnomalyResultQueryResponse(
            Document runDocument,
            int limit,
            String selectedDatasetKey,
            String selectedEquipmentId
    ) {
        AnomalyRunDetailDto run = toAnomalyRunDetail(runDocument);
        validateAnomalyRunSelection(run, selectedDatasetKey, selectedEquipmentId);

        List<Map<String, Object>> rawRows = modelTrainRepository.findAnomalyResultsByIdentity(
                run.runId(),
                run.datasetKey(),
                run.equipmentId(),
                limit
        );
        List<AnomalyResultPointDto> points = rawRows.stream()
                .map(rawRow -> toAnomalyResultPoint(run, rawRow))
                .toList();
        validateAnomalyPointIdentity(points, run);
        List<AnomalyResultPointDto> calibratedPoints = applyAlgorithmHealthCalibration(run, points);
        IntegratedHealthContext integratedHealthContext = buildIntegratedHealthContext(run, calibratedPoints, limit);
        AnomalyResultSummaryDto summary = buildAnomalyResultSummary(run, calibratedPoints, integratedHealthContext);
        return new AnomalyResultQueryResponseDto(
                run,
                summary,
                calibratedPoints,
                run.datasetKey(),
                run.equipmentId(),
                run.runId()
        );
    }

    private AnomalyResultQueryResponseDto emptyAnomalyResultQueryResponse(
            String selectedDatasetKey,
            String selectedEquipmentId,
            String selectedRunId
    ) {
        return new AnomalyResultQueryResponseDto(
                null,
                new AnomalyResultSummaryDto(
                        NO_DATA_STATUS,
                        null,
                        null,
                        null,
                        null,
                        null,
                        NO_DATA_STATUS,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0
                ),
                List.of(),
                selectedDatasetKey,
                selectedEquipmentId,
                selectedRunId
        );
    }

    private AnomalyRunOptionDto toAnomalyRunOption(Document runDocument) {
        String runId = normalizeOptionalText(runDocument.get("run_id"));
        if (runId == null) {
            return null;
        }

        String algoCode = normalizeSupportedAnomalyAlgoCode(runDocument.getString("algo_code"), false);
        if (algoCode == null) {
            return null;
        }

        return new AnomalyRunOptionDto(
                runId,
                normalizeOptionalText(runDocument.get("policy_id")),
                algoCode,
                asString(runDocument.get("algo_name"), defaultAlgoName(algoCode)),
                normalizeOptionalText(runDocument.get("dataset_key")),
                normalizeOptionalText(runDocument.get("dataset_name")),
                asString(runDocument.get("equipment_id"), normalizeOptionalText(runDocument.get(EQUIPMENT_FIELD))),
                normalizeOptionalText(runDocument.get("trigger_type")),
                asInteger(runDocument.get("window_size")),
                normalizeOptionalText(runDocument.get("status")),
                normalizeTimestamp(runDocument.get("reg_date")),
                normalizeTimestamp(runDocument.get("updated_at"))
        );
    }

    private AnomalyRunDetailDto toAnomalyRunDetail(Document runDocument) {
        String runId = normalizeRequiredObjectText(runDocument.get("run_id"), "run_id");
        String algoCode = normalizeSupportedAnomalyAlgoCode(runDocument.getString("algo_code"), true);
        String algoName = asString(runDocument.get("algo_name"), defaultAlgoName(algoCode));

        Map<String, Object> params = new LinkedHashMap<>(normalizeParamsFromObject(runDocument.get("params")));
        params.remove(INTERNAL_DATASET_KEY_PARAM);

        return new AnomalyRunDetailDto(
                runId,
                normalizeOptionalText(runDocument.get("policy_id")),
                algoCode,
                algoName,
                normalizeOptionalText(runDocument.get("dataset_key")),
                normalizeOptionalText(runDocument.get("dataset_name")),
                asString(runDocument.get("equipment_id"), normalizeOptionalText(runDocument.get(EQUIPMENT_FIELD))),
                normalizeOptionalText(runDocument.get("sensor_id")),
                normalizeOptionalText(runDocument.get("trigger_type")),
                normalizeOptionalText(runDocument.get("status")),
                normalizeSelectedColumnsForView(runDocument.get("selected_columns")),
                asInteger(runDocument.get("window_size")),
                params,
                normalizeTimestamp(runDocument.get("reg_date")),
                normalizeTimestamp(runDocument.get("updated_at"))
        );
    }

    private List<String> normalizeSelectedColumnsForView(Object selectedColumnsObject) {
        if (!(selectedColumnsObject instanceof List<?> selectedColumnsList)) {
            return List.of();
        }

        LinkedHashSet<String> selectedColumns = new LinkedHashSet<>();
        for (Object selectedColumn : selectedColumnsList) {
            String normalized = normalizeOptionalText(selectedColumn);
            if (normalized != null) {
                selectedColumns.add(normalized);
            }
        }
        return List.copyOf(selectedColumns);
    }

    private AnomalyResultPointDto toAnomalyResultPoint(AnomalyRunDetailDto run, Map<String, Object> rawRow) {
        Double anomalyScore = toDouble(rawRow.get("anomaly_score"));
        Double healthIndex = toDouble(rawRow.get("health_index"));
        Boolean isAnomaly = toBoolean(rawRow.get("is_anomaly"));
        String status = normalizeOptionalText(rawRow.get("status"));
        if (status == null && anomalyScore != null) {
            status = resolveHealthStatusByAlgorithm(run.algoCode(), anomalyScore, Boolean.TRUE.equals(isAnomaly));
        }
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
        }

        return new AnomalyResultPointDto(
                asString(rawRow.get("run_id"), run.runId()),
                run.algoCode(),
                run.algoName(),
                asString(rawRow.get("dataset_key"), run.datasetKey()),
                asString(rawRow.get("equipment_id"), asString(rawRow.get(EQUIPMENT_FIELD), run.equipmentId())),
                status,
                anomalyScore,
                healthIndex,
                isAnomaly,
                normalizeTimestamp(rawRow.get("window_start")),
                normalizeTimestamp(rawRow.get("window_end")),
                normalizeTimestamp(rawRow.get("reg_date")),
                normalizeObjectMap(rawRow.get("input_features"))
        );
    }

    private String resolveEquipmentSelection(String datasetKey, String equipmentId) {
        String normalizedEquipmentId = canonicalizeEquipmentId(equipmentId);
        if (datasetKey == null) {
            return normalizedEquipmentId;
        }

        String expectedEquipmentId = canonicalizeEquipmentId(
                equipmentMasterService.resolveEquipmentIdByDatasetKey(datasetKey)
        );
        if (normalizedEquipmentId != null
                && expectedEquipmentId != null
                && !normalizedEquipmentId.equalsIgnoreCase(expectedEquipmentId)) {
            throw new IllegalArgumentException(
                    "dataset_key and equipment_id do not match. dataset_key="
                            + datasetKey
                            + ", expected_equipment_id="
                            + expectedEquipmentId
                            + ", requested_equipment_id="
                            + normalizedEquipmentId
            );
        }

        return normalizedEquipmentId == null ? expectedEquipmentId : normalizedEquipmentId;
    }

    private void validateAnomalyRunSelection(
            AnomalyRunDetailDto run,
            String selectedDatasetKey,
            String selectedEquipmentId
    ) {
        if (run == null) {
            return;
        }

        String runDatasetKey = resolveOptionalDatasetKey(run.datasetKey());
        String runEquipmentId = canonicalizeEquipmentId(
                firstNonBlank(
                        run.equipmentId(),
                        equipmentMasterService.resolveEquipmentIdByDatasetKey(runDatasetKey)
                )
        );

        if (selectedDatasetKey != null
                && (runDatasetKey == null || !selectedDatasetKey.equalsIgnoreCase(runDatasetKey))) {
            throw new IllegalArgumentException("Selected dataset does not match the run dataset.");
        }
        if (selectedEquipmentId != null
                && (runEquipmentId == null || !selectedEquipmentId.equalsIgnoreCase(runEquipmentId))) {
            throw new IllegalArgumentException("Selected equipment does not match the run equipment.");
        }
    }

    private void validateAnomalyPointIdentity(List<AnomalyResultPointDto> points, AnomalyRunDetailDto run) {
        if (points == null || points.isEmpty() || run == null) {
            return;
        }

        String runDatasetKey = resolveOptionalDatasetKey(run.datasetKey());
        String runEquipmentId = canonicalizeEquipmentId(
                firstNonBlank(
                        run.equipmentId(),
                        equipmentMasterService.resolveEquipmentIdByDatasetKey(runDatasetKey)
                )
        );

        for (AnomalyResultPointDto point : points) {
            String pointDatasetKey = resolveOptionalDatasetKey(point.datasetKey());
            if (runDatasetKey != null && !runDatasetKey.equalsIgnoreCase(pointDatasetKey)) {
                throw new IllegalStateException("Anomaly result rows include dataset_key different from selected run.");
            }
        }

        LinkedHashSet<String> distinctEquipments = new LinkedHashSet<>();
        for (AnomalyResultPointDto point : points) {
            String equipmentId = canonicalizeEquipmentId(point.equipmentId());
            if (equipmentId != null) {
                distinctEquipments.add(equipmentId);
            }
        }

        if (runEquipmentId == null) {
            if (distinctEquipments.size() > 1) {
                throw new IllegalStateException("Anomaly result rows include multiple equipment_id values.");
            }
            return;
        }

        for (String equipmentId : distinctEquipments) {
            if (!runEquipmentId.equalsIgnoreCase(equipmentId)) {
                throw new IllegalStateException(
                        "Anomaly result rows include equipment_id different from selected run."
                );
            }
        }
    }

    private List<AnomalyResultPointDto> applyAlgorithmHealthCalibration(
            AnomalyRunDetailDto run,
            List<AnomalyResultPointDto> points
    ) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        if (run == null) {
            return points;
        }

        String normalizedAlgoCode = normalizeSupportedAlgoCode(run.algoCode(), false);
        if (normalizedAlgoCode == null) {
            return points;
        }

        List<Double> scores = new ArrayList<>(points.size());
        for (AnomalyResultPointDto point : points) {
            Double score = point.anomalyScore();
            if (score != null && Double.isFinite(score)) {
                scores.add(score);
            }
        }
        ScoreDistributionStats scoreStats = buildScoreDistributionStats(scores);

        List<AnomalyResultPointDto> calibrated = new ArrayList<>(points.size());
        HealthCalibrationResult latestCalibrationResult = null;
        for (AnomalyResultPointDto point : points) {
            Double score = point.anomalyScore();
            if (score == null || !Double.isFinite(score)) {
                calibrated.add(point);
                continue;
            }

            HealthCalibrationResult fallbackResult = evaluateHealthByAlgorithm(
                    normalizedAlgoCode,
                    score,
                    Boolean.TRUE.equals(point.isAnomaly()),
                    scoreStats
            );

            Double storedHealthIndex = point.healthIndex();
            String storedStatus = normalizeOptionalText(point.status());

            double healthIndex = storedHealthIndex != null && Double.isFinite(storedHealthIndex)
                    ? clampUnitRange(storedHealthIndex)
                    : fallbackResult.healthIndex();

            String status = storedStatus != null
                    ? storedStatus.toUpperCase(Locale.ROOT)
                    : fallbackResult.status();

            calibrated.add(new AnomalyResultPointDto(
                    point.runId(),
                    point.algoCode(),
                    point.algoName(),
                    point.datasetKey(),
                    point.equipmentId(),
                    status,
                    point.anomalyScore(),
                    healthIndex,
                    point.isAnomaly(),
                    point.windowStart(),
                    point.windowEnd(),
                    point.regDate(),
                    point.inputFeatures()
            ));
            latestCalibrationResult = fallbackResult;
        }

        if (log.isDebugEnabled() && !calibrated.isEmpty()) {
            AnomalyResultPointDto latestPoint = calibrated.get(calibrated.size() - 1);
            log.debug(
                    "Anomaly health calibration stats (view). run_id={}, algo_code={}, count={}, min={}, max={}, mean={}, stddev={}, latest_raw_score={}, latest_health_index={}, latest_status={}, latest_risk_score={}, calibration_mode={}",
                    run.runId(),
                    normalizedAlgoCode,
                    scoreStats.count(),
                    scoreStats.min(),
                    scoreStats.max(),
                    scoreStats.mean(),
                    scoreStats.stdDev(),
                    latestPoint.anomalyScore(),
                    latestPoint.healthIndex(),
                    latestPoint.status(),
                    latestCalibrationResult == null ? null : latestCalibrationResult.riskScore(),
                    latestCalibrationResult == null ? null : latestCalibrationResult.mode()
            );
        }
        return List.copyOf(calibrated);
    }

    private ScoreDistributionStats buildScoreDistributionStats(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return ScoreDistributionStats.empty();
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0D;
        int count = 0;

        for (Double score : scores) {
            if (score == null || !Double.isFinite(score)) {
                continue;
            }
            min = Math.min(min, score);
            max = Math.max(max, score);
            sum += score;
            count++;
        }

        if (count == 0) {
            return ScoreDistributionStats.empty();
        }

        double mean = sum / count;
        double varianceSum = 0D;
        for (Double score : scores) {
            if (score == null || !Double.isFinite(score)) {
                continue;
            }
            double diff = score - mean;
            varianceSum += diff * diff;
        }
        double stdDev = Math.sqrt(varianceSum / count);
        return new ScoreDistributionStats(count, min, max, mean, stdDev);
    }

    private HealthCalibrationResult evaluateHealthByAlgorithm(
            String algoCode,
            double anomalyScore,
            boolean isAnomaly,
            ScoreDistributionStats scoreStats
    ) {
        String normalizedAlgoCode = normalizeSupportedAlgoCode(algoCode, false);
        if (ALGO_AUTOENCODER.equals(normalizedAlgoCode)) {
            double riskScore = calibrateAutoencoderRisk(anomalyScore, scoreStats);
            return new HealthCalibrationResult(
                    clampUnitRange(1D - riskScore),
                    resolveAutoencoderHealthStatus(riskScore, isAnomaly),
                    riskScore,
                    "AE_ZSCORE_BLEND"
            );
        }

        double riskScore = clampUnitRange(anomalyScore);
        return new HealthCalibrationResult(
                clampUnitRange(1D - riskScore),
                resolveIsolationForestHealthStatus(riskScore, isAnomaly),
                riskScore,
                "IF_DIRECT_INVERSE"
        );
    }

    private double calibrateAutoencoderRisk(double anomalyScore, ScoreDistributionStats scoreStats) {
        double score = Double.isFinite(anomalyScore) ? anomalyScore : 0D;
        double minMaxRisk = scoreStats == null || !scoreStats.hasSpread()
                ? clampUnitRange(score)
                : normalizeScore(score, scoreStats.min(), scoreStats.max());

        double zscoreRisk = minMaxRisk;
        if (scoreStats != null && scoreStats.hasStableStdDev()) {
            double zScore = (score - scoreStats.mean()) / (scoreStats.stdDev() * AE_ZSCORE_SCALE);
            zscoreRisk = sigmoid(zScore);
        }

        return clampUnitRange((AE_MINMAX_WEIGHT * minMaxRisk) + (AE_ZSCORE_WEIGHT * zscoreRisk));
    }

    private double sigmoid(double value) {
        if (!Double.isFinite(value)) {
            return 0.5D;
        }
        if (value > 30D) {
            return 1D;
        }
        if (value < -30D) {
            return 0D;
        }
        return 1D / (1D + Math.exp(-value));
    }

    private String resolveHealthStatusByAlgorithm(String algoCode, double anomalyScore, boolean isAnomaly) {
        String normalizedAlgoCode = normalizeSupportedAlgoCode(algoCode, false);
        if (ALGO_AUTOENCODER.equals(normalizedAlgoCode)) {
            return resolveAutoencoderHealthStatus(clampUnitRange(anomalyScore), isAnomaly);
        }
        return resolveIsolationForestHealthStatus(clampUnitRange(anomalyScore), isAnomaly);
    }

    private String resolveIsolationForestHealthStatus(double riskScore, boolean isAnomaly) {
        if (!isAnomaly && riskScore < WARNING_SCORE_THRESHOLD) {
            return NORMAL_STATUS;
        }
        if (riskScore >= CRITICAL_SCORE_THRESHOLD) {
            return CRITICAL_STATUS;
        }
        return WARNING_STATUS;
    }

    private String resolveAutoencoderHealthStatus(double riskScore, boolean isAnomaly) {
        if (riskScore >= AE_CRITICAL_RISK_THRESHOLD) {
            return CRITICAL_STATUS;
        }
        if (riskScore >= AE_WARNING_RISK_THRESHOLD || isAnomaly) {
            return WARNING_STATUS;
        }
        return NORMAL_STATUS;
    }

    private Map<String, Object> normalizeObjectMap(Object value) {
        Object normalizedValue = schemaResolver.normalizeResponseValue(value);
        if (!(normalizedValue instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toString().trim();
            if (key.isEmpty()) {
                continue;
            }
            normalized.put(key, normalizeParamValue(entry.getValue()));
        }
        return normalized;
    }

    private AnomalyResultSummaryDto buildAnomalyResultSummary(
            AnomalyRunDetailDto run,
            List<AnomalyResultPointDto> points,
            IntegratedHealthContext integratedHealthContext
    ) {
        if (points.isEmpty()) {
            return new AnomalyResultSummaryDto(
                    NO_DATA_STATUS,
                    null,
                    null,
                    null,
                    null,
                    integratedHealthContext.integratedHealth(),
                    integratedHealthContext.integratedStatus(),
                    integratedHealthContext.ifNormalizedHealth(),
                    integratedHealthContext.aeNormalizedHealth(),
                    integratedHealthContext.ifScoreRaw(),
                    integratedHealthContext.aeScoreRaw(),
                    0,
                    0
            );
        }

        double scoreSum = 0D;
        int scoreCount = 0;
        double healthIndexSum = 0D;
        int healthIndexCount = 0;
        int anomalyCount = 0;

        for (AnomalyResultPointDto point : points) {
            if (point.anomalyScore() != null) {
                scoreSum += point.anomalyScore();
                scoreCount++;
            }
            if (point.healthIndex() != null) {
                healthIndexSum += point.healthIndex();
                healthIndexCount++;
            }
            if (Boolean.TRUE.equals(point.isAnomaly())) {
                anomalyCount++;
            }
        }

        AnomalyResultPointDto latestPoint = points.get(points.size() - 1);
        String summaryStatus = latestPoint.status();
        if (summaryStatus == null) {
            if (latestPoint.anomalyScore() != null) {
                summaryStatus = resolveHealthStatusByAlgorithm(
                        run.algoCode(),
                        latestPoint.anomalyScore(),
                        Boolean.TRUE.equals(latestPoint.isAnomaly())
                );
            } else {
                summaryStatus = normalizeOptionalText(run.status());
            }
        }
        if (summaryStatus == null) {
            summaryStatus = NO_DATA_STATUS;
        } else {
            summaryStatus = summaryStatus.toUpperCase(Locale.ROOT);
        }

        return new AnomalyResultSummaryDto(
                summaryStatus,
                latestPoint.anomalyScore(),
                scoreCount == 0 ? null : scoreSum / scoreCount,
                latestPoint.healthIndex(),
                healthIndexCount == 0 ? null : healthIndexSum / healthIndexCount,
                integratedHealthContext.integratedHealth(),
                integratedHealthContext.integratedStatus(),
                integratedHealthContext.ifNormalizedHealth(),
                integratedHealthContext.aeNormalizedHealth(),
                integratedHealthContext.ifScoreRaw(),
                integratedHealthContext.aeScoreRaw(),
                anomalyCount,
                points.size()
        );
    }

    private IntegratedHealthContext buildIntegratedHealthContext(
            AnomalyRunDetailDto selectedRun,
            List<AnomalyResultPointDto> selectedRunPoints,
            int limit
    ) {
        AlgorithmHealthSnapshot ifSnapshot = null;
        AlgorithmHealthSnapshot aeSnapshot = null;

        AlgorithmHealthSnapshot selectedSnapshot = buildAlgorithmSnapshotFromPoints(selectedRun.algoCode(), selectedRunPoints);
        if (ALGO_ISOLATION_FOREST.equals(selectedRun.algoCode())) {
            ifSnapshot = selectedSnapshot;
        } else if (ALGO_AUTOENCODER.equals(selectedRun.algoCode())) {
            aeSnapshot = selectedSnapshot;
        }

        String datasetKey = normalizeOptionalText(selectedRun.datasetKey());
        String equipmentId = normalizeOptionalText(selectedRun.equipmentId());

        if (ifSnapshot == null) {
            ifSnapshot = resolveAlgorithmSnapshotForComparison(datasetKey, equipmentId, ALGO_ISOLATION_FOREST, limit);
        }
        if (aeSnapshot == null) {
            aeSnapshot = resolveAlgorithmSnapshotForComparison(datasetKey, equipmentId, ALGO_AUTOENCODER, limit);
        }

        Double ifNormalizedHealth = ifSnapshot == null ? null : ifSnapshot.normalizedHealth();
        Double aeNormalizedHealth = aeSnapshot == null ? null : aeSnapshot.normalizedHealth();
        Double integratedHealth = computeIntegratedHealth(ifNormalizedHealth, aeNormalizedHealth);

        return new IntegratedHealthContext(
                integratedHealth,
                resolveIntegratedStatus(integratedHealth),
                ifNormalizedHealth,
                aeNormalizedHealth,
                ifSnapshot == null ? null : ifSnapshot.rawScore(),
                aeSnapshot == null ? null : aeSnapshot.rawScore()
        );
    }

    private AlgorithmHealthSnapshot resolveAlgorithmSnapshotForComparison(
            String datasetKey,
            String equipmentId,
            String algoCode,
            int limit
    ) {
        if (datasetKey == null) {
            return null;
        }

        Document targetRun = modelTrainRepository.findLatestModelRunByDatasetAndAlgo(datasetKey, equipmentId, algoCode);
        if (targetRun == null && equipmentId != null) {
            targetRun = modelTrainRepository.findLatestModelRunByDatasetAndAlgo(datasetKey, null, algoCode);
        }
        if (targetRun == null) {
            return null;
        }

        String targetRunId = normalizeOptionalText(targetRun.get("run_id"));
        if (targetRunId == null) {
            return null;
        }

        List<Map<String, Object>> rows = modelTrainRepository.findAnomalyResultsByRunId(targetRunId, limit);
        return buildAlgorithmSnapshotFromRawRows(algoCode, rows);
    }

    private AlgorithmHealthSnapshot buildAlgorithmSnapshotFromPoints(String algoCode, List<AnomalyResultPointDto> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        List<Double> scores = new ArrayList<>(points.size());
        Double latestScore = null;
        boolean latestIsAnomaly = false;
        Double latestHealthIndex = null;

        for (AnomalyResultPointDto point : points) {
            Double score = point.anomalyScore();
            if (score == null || !Double.isFinite(score)) {
                continue;
            }
            scores.add(score);
            latestScore = score;
            latestIsAnomaly = Boolean.TRUE.equals(point.isAnomaly());
            if (point.healthIndex() != null && Double.isFinite(point.healthIndex())) {
                latestHealthIndex = point.healthIndex();
            } else {
                latestHealthIndex = null;
            }
        }
        if (latestScore == null) {
            return null;
        }

        if (latestHealthIndex != null) {
            return new AlgorithmHealthSnapshot(latestScore, clampUnitRange(latestHealthIndex));
        }

        ScoreDistributionStats stats = buildScoreDistributionStats(scores);
        HealthCalibrationResult calibrated = evaluateHealthByAlgorithm(algoCode, latestScore, latestIsAnomaly, stats);
        return new AlgorithmHealthSnapshot(latestScore, calibrated.healthIndex());
    }

    private AlgorithmHealthSnapshot buildAlgorithmSnapshotFromRawRows(String algoCode, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        List<Double> scores = new ArrayList<>(rows.size());
        Double latestScore = null;
        boolean latestIsAnomaly = false;

        for (Map<String, Object> row : rows) {
            Double score = toDouble(row.get("anomaly_score"));
            if (score == null || !Double.isFinite(score)) {
                continue;
            }
            scores.add(score);
            latestScore = score;
            latestIsAnomaly = Boolean.TRUE.equals(toBoolean(row.get("is_anomaly")));
        }
        if (latestScore == null) {
            return null;
        }

        ScoreDistributionStats stats = buildScoreDistributionStats(scores);
        HealthCalibrationResult calibrated = evaluateHealthByAlgorithm(algoCode, latestScore, latestIsAnomaly, stats);
        return new AlgorithmHealthSnapshot(latestScore, calibrated.healthIndex());
    }

    private double normalizeScore(double score, double minScore, double maxScore) {
        if (!Double.isFinite(score) || !Double.isFinite(minScore) || !Double.isFinite(maxScore)) {
            return 0D;
        }
        if (Double.compare(maxScore, minScore) == 0) {
            return 0D;
        }
        return clampUnitRange((score - minScore) / (maxScore - minScore));
    }

    private Double computeIntegratedHealth(Double ifNormalizedHealth, Double aeNormalizedHealth) {
        double weightedSum = 0D;
        double totalWeight = 0D;

        if (ifNormalizedHealth != null && Double.isFinite(ifNormalizedHealth)) {
            weightedSum += clampUnitRange(ifNormalizedHealth) * IF_INTEGRATED_WEIGHT;
            totalWeight += IF_INTEGRATED_WEIGHT;
        }
        if (aeNormalizedHealth != null && Double.isFinite(aeNormalizedHealth)) {
            weightedSum += clampUnitRange(aeNormalizedHealth) * AE_INTEGRATED_WEIGHT;
            totalWeight += AE_INTEGRATED_WEIGHT;
        }

        // Fallback policy: if only one algorithm is available, use that normalized health as integrated_health.
        if (totalWeight == 0D) {
            return null;
        }
        return clampUnitRange(weightedSum / totalWeight);
    }

    private String resolveIntegratedStatus(Double integratedHealth) {
        if (integratedHealth == null || !Double.isFinite(integratedHealth)) {
            return NO_DATA_STATUS;
        }
        if (integratedHealth >= INTEGRATED_WARNING_HEALTH_THRESHOLD) {
            return NORMAL_STATUS;
        }
        if (integratedHealth >= INTEGRATED_CRITICAL_HEALTH_THRESHOLD) {
            return WARNING_STATUS;
        }
        return CRITICAL_STATUS;
    }

    private double clampUnitRange(double value) {
        if (!Double.isFinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    private List<String> supportedAlgoCodes() {
        return List.of(ALGO_ISOLATION_FOREST, ALGO_AUTOENCODER);
    }

    private List<AnomalyAlgorithmOptionDto> supportedAlgoOptions() {
        return List.of(
                new AnomalyAlgorithmOptionDto(ALGO_ISOLATION_FOREST, defaultAlgoName(ALGO_ISOLATION_FOREST)),
                new AnomalyAlgorithmOptionDto(ALGO_AUTOENCODER, defaultAlgoName(ALGO_AUTOENCODER))
        );
    }

    private String defaultAlgoName(String algoCode) {
        if (ALGO_ISOLATION_FOREST.equals(algoCode)) {
            return "Isolation Forest";
        }
        if (ALGO_AUTOENCODER.equals(algoCode)) {
            return "Autoencoder";
        }
        if (ALGO_RANDOM_FOREST.equals(algoCode)) {
            return "Random Forest";
        }
        return algoCode;
    }

    private String normalizeSupportedAlgoCode(String algoCode, boolean required) {
        String normalizedAlgoCode = normalizeOptionalText(algoCode);
        if (normalizedAlgoCode == null) {
            if (required) {
                throw new IllegalArgumentException("algo_code is required.");
            }
            return null;
        }

        String upperCaseAlgoCode = normalizedAlgoCode.toUpperCase(Locale.ROOT);
        if (!isSupportedAlgoCode(upperCaseAlgoCode)) {
            if (required) {
                throw new IllegalArgumentException("Unsupported algo_code: " + normalizedAlgoCode);
            }
            return null;
        }
        return upperCaseAlgoCode;
    }

    private String normalizeSupportedAnomalyAlgoCode(String algoCode, boolean required) {
        String normalizedAlgoCode = normalizeOptionalText(algoCode);
        if (normalizedAlgoCode == null) {
            if (required) {
                throw new IllegalArgumentException("algo_code is required.");
            }
            return null;
        }

        String upperCaseAlgoCode = normalizedAlgoCode.toUpperCase(Locale.ROOT);
        if (!supportedAlgoCodes().contains(upperCaseAlgoCode)) {
            if (required) {
                throw new IllegalArgumentException("Unsupported anomaly algo_code: " + normalizedAlgoCode);
            }
            return null;
        }
        return upperCaseAlgoCode;
    }

    private ExecuteModelRunResponseDto executeModelRunInternal(String runId) {
        Document modelRun = modelTrainRepository.findModelRunByRunId(runId);
        if (modelRun == null) {
            throw new IllegalArgumentException("run_id not found.");
        }

        String currentStatus = normalizeOptionalText(modelRun.get("status"));
        if (RUNNING_STATUS.equalsIgnoreCase(currentStatus)) {
            throw new IllegalArgumentException("This run is already RUNNING.");
        }

        modelTrainRepository.updateModelRunStatus(runId, RUNNING_STATUS);

        try {
            String datasetKey = resolveDatasetKeyForPolicy(
                    extractDatasetKeyFromModelRun(modelRun),
                    asString(modelRun.get("equipment_id"), normalizeOptionalText(modelRun.get(EQUIPMENT_FIELD))),
                    normalizeOptionalText(modelRun.get("sensor_id"))
            );
            String equipmentId = resolveEquipmentId(datasetKey, asString(modelRun.get("equipment_id"), normalizeOptionalText(modelRun.get(EQUIPMENT_FIELD))));
            String algoCode = normalizeRequiredObjectText(modelRun.get("algo_code"), "algo_code");
            String normalizedAlgoCode = algoCode.toUpperCase(Locale.ROOT);

            if (!isSupportedAlgoCode(normalizedAlgoCode)) {
                throw new IllegalArgumentException("Unsupported algo_code for execution: " + algoCode);
            }
            if (isRandomForestSupervisedRun(modelRun, normalizedAlgoCode, datasetKey)) {
                return executeRandomForestSupervisedRun(modelRun, runId, datasetKey, normalizedAlgoCode);
            }

            List<String> selectedColumns = normalizeSelectedColumnsFromObject(modelRun.get("selected_columns"));
            Map<String, Object> params = normalizeParamsFromObject(modelRun.get("params"));
            ParameterMappingResult parameterMappingResult = mapExecutionParameters(normalizedAlgoCode, params);

            Integer recentWindowLimit = resolveRecentWindowLimit(asInteger(modelRun.get("recent_window_limit")));
            List<Document> featureRows = recentWindowLimit == null
                    ? modelTrainRepository.findFeatureRowsByDatasetKey(datasetKey)
                    : modelTrainRepository.findLatestFeatureRowsByDatasetKey(datasetKey, recentWindowLimit);
            if (featureRows.isEmpty()) {
                String skipMessage = "Skipped: no feature rows found. dataset_key="
                        + datasetKey
                        + ", recent_window_limit="
                        + recentWindowLimit;
                modelTrainRepository.updateModelRunExecutionInfo(runId, SKIPPED_STATUS, skipMessage, null);
                return new ExecuteModelRunResponseDto(
                        runId,
                        SKIPPED_STATUS,
                        0,
                        0,
                        parameterMappingResult.metaOnlyParamKeys()
                );
            }

            List<WindowInputRow> windowInputRows = buildWindowInputRows(featureRows, selectedColumns);
            if (windowInputRows.isEmpty()) {
                String skipMessage = "Skipped: no executable feature windows were built from thisfeature.";
                modelTrainRepository.updateModelRunExecutionInfo(runId, SKIPPED_STATUS, skipMessage, null);
                return new ExecuteModelRunResponseDto(
                        runId,
                        SKIPPED_STATUS,
                        0,
                        0,
                        parameterMappingResult.metaOnlyParamKeys()
                );
            }

            List<AiInferenceResult> inferenceResults = executeInferenceInBatches(
                    runId,
                    equipmentId,
                    datasetKey,
                    normalizedAlgoCode,
                    windowInputRows,
                    parameterMappingResult
            );
            if (inferenceResults.size() != windowInputRows.size()) {
                throw new IllegalStateException(
                        "AI server result count does not match feature window count. expected="
                                + windowInputRows.size()
                                + ", actual="
                                + inferenceResults.size()
                );
            }

            List<Document> anomalyRows = buildAnomalyRows(
                    runId,
                    datasetKey,
                    equipmentId,
                    normalizedAlgoCode,
                    windowInputRows,
                    inferenceResults
            );
            int savedResultCount = modelTrainRepository.replaceAnomalyResultsByRunId(runId, anomalyRows);

            modelTrainRepository.updateModelRunStatus(runId, SUCCESS_STATUS);
            try {
                anomalyCauseService.recalculateRun(runId, datasetKey, equipmentId);
                log.info(
                        "Anomaly cause auto generation completed. runId={}, datasetKey={}, equipmentId={}",
                        runId,
                        datasetKey,
                        equipmentId
                );
            } catch (Exception exception) {
                log.warn(
                        "Anomaly cause auto generation failed. runId={}, datasetKey={}, equipmentId={}",
                        runId,
                        datasetKey,
                        equipmentId,
                        exception
                );
            }
            try {
                thresholdAlertService.recalculateRun(runId, datasetKey);
                log.info(
                        "Threshold alert auto generation completed. runId={}, datasetKey={}",
                        runId,
                        datasetKey
                );
            } catch (Exception exception) {
                log.warn(
                        "Threshold alert auto generation failed. runId={}, datasetKey={}",
                        runId,
                        datasetKey,
                        exception
                );
            }

            return new ExecuteModelRunResponseDto(
                    runId,
                    SUCCESS_STATUS,
                    windowInputRows.size(),
                    savedResultCount,
                    parameterMappingResult.metaOnlyParamKeys()
            );
        } catch (Exception exception) {
            modelTrainRepository.updateModelRunExecutionInfo(runId, FAIL_STATUS, exception.getMessage(), null);
            log.error("Model run execution failed. run_id={}", runId, exception);
            throw exception;
        }
    }

    private ExecuteModelRunResponseDto skipModelRun(String runId, String message) {
        modelTrainRepository.updateModelRunExecutionInfo(runId, SKIPPED_STATUS, message, null);
        return new ExecuteModelRunResponseDto(runId, SKIPPED_STATUS, 0, 0, List.of());
    }

    private boolean isRandomForestSupervisedRun(Document modelRun, String algoCode, String datasetKey) {
        if (ALGO_RANDOM_FOREST.equalsIgnoreCase(algoCode)) {
            return true;
        }

        String policyId = normalizeOptionalText(modelRun.get("policy_id"));
        if (POLICY_RANDOM_FOREST_SUPERVISED_V1.equalsIgnoreCase(policyId)) {
            return true;
        }

        return DATASET_THISRAWLABELED_ALL_RF_V1.equalsIgnoreCase(datasetKey)
                && ALGO_RANDOM_FOREST.equalsIgnoreCase(normalizeOptionalText(modelRun.get("algo_code")));
    }

    private ExecuteModelRunResponseDto executeRandomForestSupervisedRun(
            Document modelRun,
            String runId,
            String datasetKey,
            String normalizedAlgoCode
    ) {
        String triggerType = asString(modelRun.get("trigger_type"), TRIGGER_TYPE_MANUAL);
        String policyId = normalizeOptionalText(modelRun.get("policy_id"));
        Document policy = policyId == null ? null : modelTrainRepository.findPolicyByPolicyId(policyId);
        if (policy == null) {
            List<Document> matchedPolicies = modelTrainRepository.findPoliciesByDatasetAndAlgo(datasetKey, ALGO_RANDOM_FOREST);
            policy = selectPrimaryPolicyCandidate(matchedPolicies);
            if (policyId == null) {
                policyId = normalizeOptionalText(policy == null ? null : policy.get("policy_id"));
            }
        }

        FeatureConfigContext featureConfig = resolveFeatureConfigContext(datasetKey, "RANDOM_FOREST_EXECUTE");
        List<String> configuredSelectedColumns = normalizeSelectedColumnsForView(modelRun.get("selected_columns"));
        if (configuredSelectedColumns.isEmpty()) {
            configuredSelectedColumns = resolveFeatureSelectedColumns(featureConfig);
        }
        if (configuredSelectedColumns.isEmpty()) {
            throw new IllegalStateException(
                    "feature selected_columns is empty. dataset_key="
                            + datasetKey
                            + ", config_source="
                            + featureConfig.configSource()
            );
        }

        List<String> featureColumns = sanitizeRandomForestFeatureColumns(configuredSelectedColumns);
        if (featureColumns.isEmpty()) {
            throw new IllegalStateException(
                    "No valid feature columns remain after excluded-column filtering. dataset_key=" + datasetKey
            );
        }

        Map<String, Object> mergedParams = new LinkedHashMap<>();
        if (policy != null) {
            mergedParams.putAll(normalizeParamsFromObject(policy.get("params")));
        }
        mergedParams.putAll(normalizeParamsFromObject(modelRun.get("params")));

        Map<String, Object> resolvedParams = resolveRandomForestExecutionParams(mergedParams);
        int sampleLimit = resolveRandomForestSampleLimit(resolvedParams.get(PARAM_TRAIN_SAMPLE_LIMIT));
        resolvedParams.put(PARAM_TRAIN_SAMPLE_LIMIT, sampleLimit);

        long totalEligibleCount = modelTrainRepository.countLabeledRowsByLabels(datasetKey, List.of(0, 1));
        if (totalEligibleCount <= 0) {
            return skipModelRun(
                    runId,
                    "Skipped: labeled data is not prepared yet. No label=0/1 rows found. dataset_key=" + datasetKey
            );
        }

        long excludedUnknownCountLong = modelTrainRepository.countLabeledRowsByLabel(datasetKey, 9);
        int excludedUnknownCount = safeLongToInt(excludedUnknownCountLong);

        // Safety guard for MVP: keep payload bounded to avoid large Java heap and Java->Python payload spikes.
        List<Document> labeledRows = modelTrainRepository.findLabeledRowsByLabels(datasetKey, List.of(0, 1), sampleLimit);
        if (labeledRows.isEmpty()) {
            return skipModelRun(
                    runId,
                    "Skipped: labeled data is not prepared yet. No sampled label=0/1 rows. dataset_key=" + datasetKey
            );
        }

        List<Map<String, Object>> payloadRows = new ArrayList<>(labeledRows.size());
        Map<Integer, RandomForestPayloadRowMeta> payloadRowMetaByIndex = new LinkedHashMap<>();
        int normalCount = 0;
        int anomalyCount = 0;

        for (int index = 0; index < labeledRows.size(); index++) {
            Document row = labeledRows.get(index);
            Integer label = asInteger(row.get("label"));
            if (label == null || (label != 0 && label != 1)) {
                continue;
            }

            if (label == 0) {
                normalCount++;
            } else {
                anomalyCount++;
            }

            String labeledDocId = normalizeIdentifier(row.get("_id"));
            if (labeledDocId == null) {
                labeledDocId = "ROW_" + index;
            }
            String sourceId = normalizeIdentifier(row.get("source_id"));
            Map<String, Object> inputFeatures = buildRandomForestInputFeatures(row, featureColumns);

            Map<String, Object> payloadRow = new LinkedHashMap<>();
            payloadRow.put("source_index", index);
            payloadRow.put("labeled_doc_id", labeledDocId);
            payloadRow.put("source_id", sourceId);
            payloadRow.put("label", label);
            payloadRow.put("input_features", inputFeatures);
            payloadRows.add(payloadRow);

            payloadRowMetaByIndex.put(index, new RandomForestPayloadRowMeta(labeledDocId, sourceId, inputFeatures));
        }

        if (payloadRows.size() < 2) {
            return skipModelRun(
                    runId,
                    "Skipped: Random Forest requires at least 2 sampled rows. sampled_rows=" + payloadRows.size()
            );
        }
        if (normalCount == 0 || anomalyCount == 0) {
            return skipModelRun(
                    runId,
                    "Skipped: Random Forest requires both label=0 and label=1 rows. normal_count="
                            + normalCount
                            + ", anomaly_count="
                            + anomalyCount
            );
        }

        log.info(
                "Random Forest supervised label distribution. run_id={}, dataset_key={}, sampled_count={}, normal_count={}, anomaly_count={}, excluded_unknown_count={}, total_eligible_count={}, sample_limit={}",
                runId,
                datasetKey,
                payloadRows.size(),
                normalCount,
                anomalyCount,
                excludedUnknownCount,
                totalEligibleCount,
                sampleLimit
        );

        Map<String, Object> aiPayload = new LinkedHashMap<>();
        aiPayload.put("run_id", runId);
        aiPayload.put("dataset_key", datasetKey);
        aiPayload.put("algo_code", ALGO_RANDOM_FOREST);
        aiPayload.put("feature_columns", featureColumns);
        aiPayload.put("rows", payloadRows);
        aiPayload.put("params", toRandomForestAiParams(resolvedParams));
        aiPayload.put("label_field", asString(resolvedParams.get(PARAM_LABEL_FIELD), RF_DEFAULT_LABEL_FIELD));

        AiRandomForestExecutionResult executionResult = aiModelExecutionClient.executeRandomForest(aiPayload);
        List<AiRandomForestPredictionResult> predictions = executionResult.predictions() == null
                ? List.of()
                : executionResult.predictions();

        List<Document> classificationRows = new ArrayList<>();
        Date now = new Date();
        for (AiRandomForestPredictionResult prediction : predictions) {
            String splitType = normalizeOptionalText(prediction.splitType());
            if (splitType == null) {
                continue;
            }
            String normalizedSplitType = splitType.toUpperCase(Locale.ROOT);

            // Storage policy for MVP: persist TEST split only to keep collection size bounded.
            if (!"TEST".equals(normalizedSplitType)) {
                continue;
            }

            Integer sourceIndex = prediction.sourceIndex();
            RandomForestPayloadRowMeta payloadMeta = sourceIndex == null ? null : payloadRowMetaByIndex.get(sourceIndex);
            String labeledDocId = firstNonBlank(
                    normalizeOptionalText(prediction.labeledDocId()),
                    payloadMeta == null ? null : payloadMeta.labeledDocId()
            );
            if (labeledDocId == null) {
                labeledDocId = "ROW_" + (sourceIndex == null ? classificationRows.size() : sourceIndex);
            }

            String sourceId = firstNonBlank(
                    normalizeOptionalText(prediction.sourceId()),
                    payloadMeta == null ? null : payloadMeta.sourceId()
            );
            Integer actualLabel = prediction.actualLabel();
            Integer predictedLabel = prediction.predictionLabel();
            if (actualLabel == null || predictedLabel == null) {
                continue;
            }

            Document classificationRow = new Document();
            classificationRow.put("run_id", runId);
            classificationRow.put("dataset_key", datasetKey);
            if (policyId != null) {
                classificationRow.put("policy_id", policyId);
            }
            classificationRow.put("algo_code", normalizedAlgoCode);
            classificationRow.put("algo_name", defaultAlgoName(normalizedAlgoCode));
            classificationRow.put("labeled_doc_id", labeledDocId);
            if (sourceId != null) {
                classificationRow.put("source_id", sourceId);
            }
            classificationRow.put("actual_label", actualLabel);
            classificationRow.put("prediction_label", predictedLabel);
            classificationRow.put("prediction_probability", defaultIfNull(prediction.predictionProbability(), 0D));
            classificationRow.put("prediction_probability_normal", defaultIfNull(prediction.predictionProbabilityNormal(), 0D));
            classificationRow.put("prediction_probability_anomaly", defaultIfNull(prediction.predictionProbabilityAnomaly(), 0D));
            classificationRow.put("split_type", normalizedSplitType);
            classificationRow.put("input_features", payloadMeta == null ? Map.of() : payloadMeta.inputFeatures());
            classificationRow.put("correct_yn", actualLabel.equals(predictedLabel) ? "Y" : "N");
            classificationRow.put("error_type", resolvePredictionErrorType(
                    actualLabel,
                    predictedLabel,
                    normalizeOptionalText(prediction.errorType())
            ));
            classificationRow.put("reg_date", now);
            classificationRows.add(classificationRow);
        }

        int savedClassificationCount = modelTrainRepository.replaceClassificationResultsByRunId(runId, classificationRows);

        int trainCount = defaultIfNull(executionResult.trainCount(), 0);
        int testCount = defaultIfNull(executionResult.testCount(), 0);
        int totalCount = defaultIfNull(executionResult.totalCount(), payloadRows.size());
        int tp = defaultIfNull(executionResult.tp(), 0);
        int tn = defaultIfNull(executionResult.tn(), 0);
        int fp = defaultIfNull(executionResult.fp(), 0);
        int fn = defaultIfNull(executionResult.fn(), 0);

        Document modelEvalDocument = new Document();
        modelEvalDocument.put("run_id", runId);
        modelEvalDocument.put("dataset_key", datasetKey);
        if (policyId != null) {
            modelEvalDocument.put("policy_id", policyId);
        }
        modelEvalDocument.put("algo_code", normalizedAlgoCode);
        modelEvalDocument.put("algo_name", defaultAlgoName(normalizedAlgoCode));
        modelEvalDocument.put("label_version", resolveRandomForestLabelVersion(resolvedParams, labeledRows));
        modelEvalDocument.put("total_count", totalCount);
        modelEvalDocument.put("train_count", trainCount);
        modelEvalDocument.put("test_count", testCount);
        modelEvalDocument.put("validation_count", 0);
        modelEvalDocument.put("excluded_unknown_count", excludedUnknownCount);
        modelEvalDocument.put("normal_count", normalCount);
        modelEvalDocument.put("anomaly_count", anomalyCount);
        modelEvalDocument.put("accuracy", defaultIfNull(executionResult.accuracy(), 0D));
        modelEvalDocument.put("precision", defaultIfNull(executionResult.precision(), 0D));
        modelEvalDocument.put("recall", defaultIfNull(executionResult.recall(), 0D));
        modelEvalDocument.put("f1_score", defaultIfNull(executionResult.f1Score(), 0D));
        modelEvalDocument.put("tp", tp);
        modelEvalDocument.put("tn", tn);
        modelEvalDocument.put("fp", fp);
        modelEvalDocument.put("fn", fn);
        modelEvalDocument.put("train_valid_ratio", RF_DEFAULT_TRAIN_VALID_RATIO);
        modelEvalDocument.put("class_weight", asString(resolvedParams.get(PARAM_CLASS_WEIGHT), RF_DEFAULT_CLASS_WEIGHT));
        modelEvalDocument.put("random_state", asInteger(resolvedParams.get(PARAM_SEED)));
        modelEvalDocument.put("params", new LinkedHashMap<>(resolvedParams));
        modelEvalDocument.put("feature_columns", featureColumns);
        modelEvalDocument.put("excluded_columns", RF_EXCLUDED_COLUMNS);
        modelEvalDocument.put("feature_importances", toModelEvalFeatureImportances(executionResult.featureImportances()));
        modelEvalDocument.put("evaluation_method", "TRAIN_TEST_SPLIT_8_2_SEED_42_ZERO_DIVISION_SAFE");
        modelEvalDocument.put("message", buildRandomForestRunMessage(totalEligibleCount, payloadRows.size(), savedClassificationCount));
        modelEvalDocument.put("reg_date", now);
        modelEvalDocument.put("updated_at", now);

        modelTrainRepository.replaceModelEvalByRunId(runId, modelEvalDocument);

        Map<String, Object> runParams = new LinkedHashMap<>(resolvedParams);
        runParams.put(INTERNAL_DATASET_KEY_PARAM, datasetKey);
        modelTrainRepository.updateModelRunExecutionInfo(
                runId,
                SUCCESS_STATUS,
                buildRandomForestRunMessage(totalEligibleCount, payloadRows.size(), savedClassificationCount),
                runParams
        );

        if (TRIGGER_TYPE_SCHEDULE.equalsIgnoreCase(triggerType)) {
            log.info(
                    "Random Forest scheduler execution completed. run_id={}, policy_id={}, dataset_key={}, algo_code={}, trigger_type={}, sampled_rows={}, total_eligible_rows={}, normal_count={}, anomaly_count={}, excluded_unknown_count={}, saved_test_rows={}",
                    runId,
                    policyId,
                    datasetKey,
                    normalizedAlgoCode,
                    triggerType,
                    payloadRows.size(),
                    totalEligibleCount,
                    normalCount,
                    anomalyCount,
                    excludedUnknownCount,
                    savedClassificationCount
            );
        }

        return new ExecuteModelRunResponseDto(
                runId,
                SUCCESS_STATUS,
                totalCount,
                savedClassificationCount,
                List.of()
        );
    }

    private List<String> sanitizeRandomForestFeatureColumns(List<String> selectedColumns) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String selectedColumn : selectedColumns) {
            String normalized = normalizeOptionalText(selectedColumn);
            if (normalized == null) {
                continue;
            }
            if (RF_EXCLUDED_COLUMNS.stream().anyMatch(excluded -> excluded.equalsIgnoreCase(normalized))) {
                continue;
            }
            sanitized.add(normalized);
        }
        return sanitized.stream().distinct().toList();
    }

    private Map<String, Object> resolveRandomForestExecutionParams(Map<String, Object> params) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        Map<String, Object> normalized = params == null ? Map.of() : params;

        resolved.put(PARAM_N_ESTIMATORS, positiveOrDefault(asInteger(normalized.get(PARAM_N_ESTIMATORS)), RF_DEFAULT_N_ESTIMATORS));
        resolved.put(PARAM_MAX_DEPTH, positiveOrDefault(asInteger(normalized.get(PARAM_MAX_DEPTH)), RF_DEFAULT_MAX_DEPTH));
        resolved.put(PARAM_MIN_SAMPLES_SPLIT, Math.max(2, positiveOrDefault(asInteger(normalized.get(PARAM_MIN_SAMPLES_SPLIT)), RF_DEFAULT_MIN_SAMPLES_SPLIT)));
        resolved.put(PARAM_MIN_SAMPLES_LEAF, Math.max(1, positiveOrDefault(asInteger(normalized.get(PARAM_MIN_SAMPLES_LEAF)), RF_DEFAULT_MIN_SAMPLES_LEAF)));
        resolved.put(PARAM_MAX_FEATURES, asString(normalized.get(PARAM_MAX_FEATURES), RF_DEFAULT_MAX_FEATURES).toLowerCase(Locale.ROOT));
        resolved.put(PARAM_CLASS_WEIGHT, asString(normalized.get(PARAM_CLASS_WEIGHT), RF_DEFAULT_CLASS_WEIGHT));
        resolved.put(PARAM_TRAIN_VALID_RATIO, RF_DEFAULT_TRAIN_VALID_RATIO);
        resolved.put(PARAM_SEED, positiveOrDefault(asInteger(normalized.get(PARAM_SEED)), RF_DEFAULT_SEED));
        resolved.put(PARAM_EARLY_STOPPING, false);
        resolved.put(PARAM_LABEL_FIELD, asString(normalized.get(PARAM_LABEL_FIELD), RF_DEFAULT_LABEL_FIELD));
        resolved.put(PARAM_LABEL_VERSION, asString(normalized.get(PARAM_LABEL_VERSION), RF_DEFAULT_LABEL_VERSION));

        Integer requestedSampleLimit = asInteger(normalized.get(PARAM_TRAIN_SAMPLE_LIMIT));
        if (requestedSampleLimit != null && requestedSampleLimit > 0) {
            resolved.put(PARAM_TRAIN_SAMPLE_LIMIT, requestedSampleLimit);
        } else {
            resolved.put(PARAM_TRAIN_SAMPLE_LIMIT, Math.max(defaultSupervisedSampleLimit, 1));
        }

        return resolved;
    }

    private int resolveRandomForestSampleLimit(Object value) {
        Integer requested = asInteger(value);
        if (requested != null && requested > 0) {
            return requested;
        }
        return Math.max(defaultSupervisedSampleLimit, 1);
    }

    private Map<String, Object> buildRandomForestInputFeatures(Document row, List<String> featureColumns) {
        Map<String, Object> inputFeatures = new LinkedHashMap<>();
        for (String featureColumn : featureColumns) {
            inputFeatures.put(featureColumn, safeNumericValue(row.get(featureColumn)));
        }
        return inputFeatures;
    }

    private Map<String, Object> toRandomForestAiParams(Map<String, Object> resolvedParams) {
        Map<String, Object> aiParams = new LinkedHashMap<>();
        aiParams.put("n_estimators", asInteger(resolvedParams.get(PARAM_N_ESTIMATORS)));
        aiParams.put("max_depth", asInteger(resolvedParams.get(PARAM_MAX_DEPTH)));
        aiParams.put("min_samples_split", asInteger(resolvedParams.get(PARAM_MIN_SAMPLES_SPLIT)));
        aiParams.put("min_samples_leaf", asInteger(resolvedParams.get(PARAM_MIN_SAMPLES_LEAF)));
        aiParams.put("max_features", asString(resolvedParams.get(PARAM_MAX_FEATURES), RF_DEFAULT_MAX_FEATURES));
        aiParams.put("class_weight", asString(resolvedParams.get(PARAM_CLASS_WEIGHT), RF_DEFAULT_CLASS_WEIGHT));
        aiParams.put("train_valid_ratio", RF_DEFAULT_TRAIN_VALID_RATIO_NUMERIC);
        aiParams.put("seed", asInteger(resolvedParams.get(PARAM_SEED)));
        return aiParams;
    }

    private List<Map<String, Object>> toModelEvalFeatureImportances(
            List<AiRandomForestFeatureImportanceResult> aiFeatureImportances
    ) {
        if (aiFeatureImportances == null || aiFeatureImportances.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (AiRandomForestFeatureImportanceResult raw : aiFeatureImportances) {
            if (raw == null) {
                continue;
            }
            String feature = normalizeOptionalText(raw.feature());
            if (feature == null) {
                continue;
            }
            Double importance = defaultIfNull(raw.importance(), 0D);
            if (!Double.isFinite(importance)) {
                importance = 0D;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("feature", feature);
            item.put("importance", importance);
            normalized.add(item);
        }

        if (normalized.isEmpty()) {
            return List.of();
        }

        normalized.sort((left, right) -> {
            double leftImportance = ((Number) left.get("importance")).doubleValue();
            double rightImportance = ((Number) right.get("importance")).doubleValue();
            int compareByImportance = Double.compare(rightImportance, leftImportance);
            if (compareByImportance != 0) {
                return compareByImportance;
            }
            return asString(left.get("feature"), "").compareTo(asString(right.get("feature"), ""));
        });

        List<Map<String, Object>> ranked = new ArrayList<>(normalized.size());
        for (int index = 0; index < normalized.size(); index++) {
            Map<String, Object> source = normalized.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", index + 1);
            item.put("feature", source.get("feature"));
            item.put("importance", source.get("importance"));
            ranked.add(item);
        }
        return ranked;
    }

    private String resolvePredictionErrorType(Integer actualLabel, Integer predictedLabel, String fallbackErrorType) {
        if (actualLabel == null || predictedLabel == null) {
            return firstNonBlank(fallbackErrorType, "UNKNOWN");
        }
        if (actualLabel == 1 && predictedLabel == 1) {
            return "TP";
        }
        if (actualLabel == 0 && predictedLabel == 0) {
            return "TN";
        }
        if (actualLabel == 0 && predictedLabel == 1) {
            return "FP";
        }
        if (actualLabel == 1 && predictedLabel == 0) {
            return "FN";
        }
        return firstNonBlank(fallbackErrorType, "UNKNOWN");
    }

    private String resolveRandomForestLabelVersion(Map<String, Object> resolvedParams, List<Document> labeledRows) {
        String fromParams = normalizeOptionalText(resolvedParams.get(PARAM_LABEL_VERSION));
        if (fromParams != null) {
            return fromParams;
        }
        if (labeledRows != null) {
            for (Document row : labeledRows) {
                String fromRow = normalizeOptionalText(row.get("label_version"));
                if (fromRow != null) {
                    return fromRow;
                }
            }
        }
        return RF_DEFAULT_LABEL_VERSION;
    }

    private String buildRandomForestRunMessage(long totalEligibleCount, int sampledCount, int savedClassificationCount) {
        if (totalEligibleCount > sampledCount) {
            return "Random Forest run completed with sampling limit. sampled_rows="
                    + sampledCount
                    + ", total_eligible_rows="
                    + totalEligibleCount
                    + ", saved_test_rows="
                    + savedClassificationCount;
        }
        return "Random Forest run completed. sampled_rows="
                + sampledCount
                + ", saved_test_rows="
                + savedClassificationCount;
    }

    private int safeLongToInt(long value) {
        if (value <= 0) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return value;
    }

    private double safeNumericValue(Object rawValue) {
        Double numericValue = toDouble(rawValue);
        if (numericValue == null || !Double.isFinite(numericValue)) {
            return 0D;
        }
        return numericValue;
    }

    private String normalizeIdentifier(Object value) {
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("ObjectId(\"") && normalized.endsWith("\")") && normalized.length() > 12) {
            return normalized.substring(10, normalized.length() - 2);
        }
        return normalized;
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

    private Double defaultIfNull(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private Integer defaultIfNull(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private List<Document> resolveRequestedPoliciesForTrigger(ModelTrainAutoTriggerRequestDto request) {
        if (request != null && request.policyId() != null && !request.policyId().isBlank()) {
            Document policy = modelTrainRepository.findPolicyByPolicyId(request.policyId().trim());
            if (policy == null) {
                return List.of();
            }
            if (!isPolicyEnabledForAutoTrain(policy) && !isRandomForestPolicy(policy)) {
                return List.of();
            }
            return List.of(policy);
        }

        List<Document> activeSelections = modelTrainRepository.findEnabledModelActives();
        if (activeSelections.isEmpty()) {
            return List.of();
        }

        String algoCodeFilter = request == null ? null : normalizeSupportedAlgoCode(request.algoCode(), false);
        String datasetKeyFilter = request == null
                ? null
                : resolveOptionalDatasetKey(request.datasetKey());
        if (datasetKeyFilter != null && !equipmentMasterService.isRuntimeOperationalDatasetKey(datasetKeyFilter)) {
            return List.of();
        }

        List<Document> requestedPolicies = new ArrayList<>();
        for (Document activeSelection : activeSelections) {
            String activeDatasetKey = resolveDatasetKeyForPolicy(
                    normalizeOptionalText(activeSelection.get("dataset_key")),
                    null,
                    null
            );
            if (!equipmentMasterService.isRuntimeOperationalDatasetKey(activeDatasetKey)) {
                continue;
            }
            if (datasetKeyFilter != null && !datasetKeyFilter.equals(activeDatasetKey)) {
                continue;
            }

            String activePolicyId = normalizeOptionalText(activeSelection.get("active_policy_id"));
            if (activePolicyId == null) {
                continue;
            }

            Document policy = modelTrainRepository.findPolicyByPolicyId(activePolicyId);
            if (policy == null || !isPolicyEnabledForAutoTrain(policy)) {
                continue;
            }

            if (algoCodeFilter != null) {
                String policyAlgoCode = normalizeSupportedAlgoCode(asString(policy.get("algo_code"), null), false);
                if (!algoCodeFilter.equals(policyAlgoCode)) {
                    continue;
                }
            }
            requestedPolicies.add(policy);
        }

        return requestedPolicies;
    }

    private boolean isAdminTriggerRequest(ModelTrainAutoTriggerRequestDto request) {
        if (request == null) {
            return false;
        }

        String requestedRole = normalizeOptionalText(request.requestedByRole());
        return requestedRole != null && "admin".equalsIgnoreCase(requestedRole);
    }

    private boolean isPolicyDue(
            Document activeSelection,
            Document policy,
            String datasetKey,
            String activePolicyId,
            Instant now
    ) {
        Integer schedulerIntervalMinutes = asInteger(policy.get("scheduler_interval_minutes"));
        int intervalMinutes = resolveSchedulerIntervalMinutes(schedulerIntervalMinutes);
        long intervalMillis = intervalMinutes * 60_000L;

        Document latestRun = modelTrainRepository.findLatestModelRunByDatasetKey(datasetKey);
        Instant lastRunTime = latestRun == null ? null : parseInstantOrNull(latestRun.get("reg_date"));
        if (latestRun != null && lastRunTime == null) {
            logSchedulerDueDecision(
                    datasetKey,
                    activePolicyId,
                    null,
                    now,
                    intervalMinutes,
                    null,
                    "EXECUTE_LAST_RUN_REG_DATE_UNPARSABLE"
            );
            return true;
        }

        Instant activeUpdatedAt = parseInstantOrNull(activeSelection == null ? null : activeSelection.get("updated_at"));
        if (lastRunTime == null) {
            logSchedulerDueDecision(
                    datasetKey,
                    activePolicyId,
                    null,
                    now,
                    intervalMinutes,
                    null,
                    "EXECUTE_NO_PREVIOUS_RUN"
            );
            return true;
        }

        long diffMillis = now.toEpochMilli() - lastRunTime.toEpochMilli();
        if (activeUpdatedAt != null && activeUpdatedAt.isAfter(lastRunTime)) {
            logSchedulerDueDecision(
                    datasetKey,
                    activePolicyId,
                    lastRunTime,
                    now,
                    intervalMinutes,
                    diffMillis,
                    "EXECUTE_ACTIVE_POLICY_CHANGED"
            );
            return true;
        }

        if (diffMillis >= intervalMillis) {
            logSchedulerDueDecision(
                    datasetKey,
                    activePolicyId,
                    lastRunTime,
                    now,
                    intervalMinutes,
                    diffMillis,
                    "EXECUTE_INTERVAL_ELAPSED"
            );
            return true;
        }

        logSchedulerDueDecision(
                datasetKey,
                activePolicyId,
                lastRunTime,
                now,
                intervalMinutes,
                diffMillis,
                "SKIP_INTERVAL_NOT_ELAPSED"
        );
        return false;
    }

    private PolicyRunOutcome executePolicy(Document policy, String triggerType, boolean forceRun) {
        String policyId = asString(policy.get("policy_id"), "");
        String datasetKey = resolveDatasetKeyForPolicy(
                extractDatasetKeyFromPolicy(policy),
                null,
                null
        );
        FeatureConfigContext featureConfig = resolveFeatureConfigContext(datasetKey, "MODEL_POLICY_EXECUTE");
        Document datasetConfig = featureConfig.datasetConfig();
        String datasetName = resolveFeatureDatasetName(featureConfig);
        String datasetLabel = schemaResolver.buildDatasetLabel(
                datasetKey,
                datasetName == null ? DEFAULT_DATASET_NAME : datasetName
        );

        if (policyId.isBlank()) {
            return new PolicyRunOutcome(
                    new ModelTrainAutoTriggerResultDto(
                            "",
                            datasetLabel,
                            FAIL_STATUS,
                            "Invalid model-train policy.",
                            null,
                            0,
                            0,
                            0,
                            0
                    ),
                    false
            );
        }

        boolean allowManualSupervisedRun = isRandomForestPolicy(policy) && TRIGGER_TYPE_MANUAL.equalsIgnoreCase(triggerType);
        if (!isPolicyEnabledForAutoTrain(policy) && !allowManualSupervisedRun) {
            String reason = "Skipped: policy is disabled by use_flag/auto_train_enabled.";
            log.info("{}. policy_id={}, dataset_key={}", reason, policyId, datasetKey);
            return buildSkippedPolicyOutcome(policyId, datasetLabel, reason, 0, 0);
        }

        List<String> selectedColumns = resolveFeatureSelectedColumns(featureConfig);
        if (selectedColumns.isEmpty()) {
            String reason = "Skipped: feature selected_columns is empty. dataset_key="
                    + datasetKey
                    + ", config_source="
                    + featureConfig.configSource();
            log.info("{}. policy_id={}", reason, policyId);
            return buildSkippedPolicyOutcome(policyId, datasetLabel, reason, 0, 0);
        }

        Integer configuredWindowSize = resolveFeatureWindowSizeOrNull(featureConfig);
        int windowSize;
        try {
            windowSize = resolveWindowSize(configuredWindowSize);
        } catch (IllegalArgumentException windowSizeException) {
            String reason = "Skipped: invalid feature window_size. dataset_key="
                    + datasetKey
                    + ", value="
                    + configuredWindowSize
                    + ", config_source="
                    + featureConfig.configSource();
            log.info("{}. policy_id={}", reason, policyId);
            return buildSkippedPolicyOutcome(policyId, datasetLabel, reason, 0, 0);
        }

        String algoCode;
        try {
            algoCode = normalizeSupportedAlgoCode(asString(policy.get("algo_code"), null), true);
        } catch (IllegalArgumentException invalidAlgoCodeException) {
            String reason = "Invalid policy algo_code. " + invalidAlgoCodeException.getMessage();
            log.info("{}. policy_id={}", reason, policyId);
            return new PolicyRunOutcome(
                    new ModelTrainAutoTriggerResultDto(
                            policyId,
                            datasetLabel,
                            FAIL_STATUS,
                            reason,
                            null,
                            0,
                            0,
                            0,
                            0
                    ),
                    false
            );
        }
        String algoName = asString(policy.get("algo_name"), defaultAlgoName(algoCode));
        boolean supervisedRandomForest = ALGO_RANDOM_FOREST.equals(algoCode);
        if (!supervisedRandomForest && !equipmentMasterService.isRuntimeOperationalDatasetKey(datasetKey)) {
            String reason = "Skipped: dataset is not an active AI operational dataset.";
            log.info("{}. policy_id={}, dataset_key={}", reason, policyId, datasetKey);
            return buildSkippedPolicyOutcome(policyId, datasetLabel, reason, 0, 0);
        }
        if (supervisedRandomForest && TRIGGER_TYPE_SCHEDULE.equalsIgnoreCase(triggerType)) {
            String reason = "Skipped: supervised Random Forest auto-scheduler is disabled until labeled data is prepared.";
            log.info("{}. policy_id={}, dataset_key={}", reason, policyId, datasetKey);
            return buildSkippedPolicyOutcome(policyId, datasetLabel, reason, 0, 0);
        }
        Map<String, Object> params = normalizeParamsFromObject(policy.get("params"));
        String equipmentId = resolveEquipmentId(datasetKey, asString(datasetConfig.get("equipment_id"), normalizeOptionalText(datasetConfig.get(EQUIPMENT_FIELD))));
        String sensorId = resolveSensorId(datasetKey, datasetConfig.get("sensor_id"));
        Integer recentWindowLimit;
        try {
            recentWindowLimit = resolveRecentWindowLimit(asInteger(policy.get("recent_window_limit")));
        } catch (IllegalArgumentException invalidWindowLimitException) {
            String reason = "Invalid recent_window_limit in policy. " + invalidWindowLimitException.getMessage();
            log.info("{}. policy_id={}", reason, policyId);
            return new PolicyRunOutcome(
                    new ModelTrainAutoTriggerResultDto(
                            policyId,
                            datasetLabel,
                            FAIL_STATUS,
                            reason,
                            null,
                            0,
                            0,
                            0,
                            0
                    ),
                    false
            );
        }

        long totalFeatureCount;
        long newFeatureCount;
        if (supervisedRandomForest) {
            totalFeatureCount = modelTrainRepository.countLabeledRowsByLabels(datasetKey, List.of(0, 1));
            newFeatureCount = totalFeatureCount;
        } else {
            totalFeatureCount = modelTrainRepository.countFeatureRowsByDatasetKey(datasetKey);
            Object lastTrainedWindowEnd = resolveLastTrainedWindowEnd(policyId);
            newFeatureCount = modelTrainRepository.countFeatureRowsByDatasetKeyAfterWindowEnd(datasetKey, lastTrainedWindowEnd);
        }

        int minTotalFeatureCount = resolveMinTotalFeatureCount(asInteger(policy.get("min_total_feature_count")));
        int minNewFeatureCount = resolveMinNewFeatureCount(asInteger(policy.get("min_new_feature_count")));

        if (!supervisedRandomForest && !forceRun && totalFeatureCount < minTotalFeatureCount) {
            String reason = "Skipped: total feature count threshold not met. required=" + minTotalFeatureCount + ", actual=" + totalFeatureCount;
            log.info("{}. policy_id={}, dataset_key={}", reason, policyId, datasetKey);
            return buildSkippedPolicyOutcome(policyId, datasetLabel, reason, newFeatureCount, totalFeatureCount);
        }

        if (!supervisedRandomForest && !forceRun && newFeatureCount < minNewFeatureCount) {
            String reason = "Skipped: new feature count threshold not met. required=" + minNewFeatureCount + ", actual=" + newFeatureCount;
            log.info("{}. policy_id={}, dataset_key={}", reason, policyId, datasetKey);
            return buildSkippedPolicyOutcome(policyId, datasetLabel, reason, newFeatureCount, totalFeatureCount);
        }

        try {
            String runId = generateUniqueRunId();
            Document runDocument = buildRunDocument(
                    runId,
                    datasetKey,
                    equipmentId,
                    sensorId,
                    selectedColumns,
                    windowSize,
                    datasetName,
                    firstNonBlank(resolveFeatureSourceCollection(featureConfig), DEFAULT_SOURCE_COLLECTION),
                    firstNonBlank(resolveFeatureTargetCollection(featureConfig), "thisfeature"),
                    algoCode,
                    algoName,
                    params,
                    policyId,
                    triggerType,
                    recentWindowLimit,
                    featureConfig.configSource(),
                    featureConfig.configMessage()
            );
            modelTrainRepository.insertModelRun(runDocument);

            ExecuteModelRunResponseDto executeResult = executeModelRunInternal(runId);

            return new PolicyRunOutcome(
                    new ModelTrainAutoTriggerResultDto(
                            policyId,
                            datasetLabel,
                            executeResult.status(),
                            "Model training execution completed.",
                            runId,
                            executeResult.processedWindowCount(),
                            executeResult.savedResultCount(),
                            newFeatureCount,
                            totalFeatureCount
                    ),
                    SUCCESS_STATUS.equalsIgnoreCase(executeResult.status())
            );
        } catch (Exception exception) {
            log.error("Model-train policy execution failed. policyId={}", policyId, exception);
            return new PolicyRunOutcome(
                    new ModelTrainAutoTriggerResultDto(
                            policyId,
                            datasetLabel,
                            FAIL_STATUS,
                            exception.getMessage(),
                            null,
                            0,
                            0,
                            newFeatureCount,
                            totalFeatureCount
                    ),
                    false
            );
        }
    }

    private PolicyRunOutcome buildSkippedPolicyOutcome(
            String policyId,
            String datasetLabel,
            String reason,
            long newFeatureCount,
            long totalFeatureCount
    ) {
        return new PolicyRunOutcome(
                new ModelTrainAutoTriggerResultDto(
                        policyId,
                        datasetLabel,
                        SKIPPED_STATUS,
                        reason,
                        null,
                        0,
                        0,
                        newFeatureCount,
                        totalFeatureCount
                ),
                false
        );
    }

    private PolicyRunOutcome buildUnhandledPolicyFailureOutcome(Document policy, Exception exception) {
        String policyId = asString(policy == null ? null : policy.get("policy_id"), "");
        String datasetKey = resolveDatasetKeyForPolicy(
                extractDatasetKeyFromPolicy(policy),
                null,
                null
        );
        String datasetLabel = schemaResolver.buildDatasetLabel(datasetKey, DEFAULT_DATASET_NAME);
        String message = exception == null || normalizeOptionalText(exception.getMessage()) == null
                ? "Unhandled model-train execution error."
                : exception.getMessage();
        return new PolicyRunOutcome(
                new ModelTrainAutoTriggerResultDto(
                        policyId,
                        datasetLabel,
                        FAIL_STATUS,
                        message,
                        null,
                        0,
                        0,
                        0,
                        0
                ),
                false
        );
    }

    private ModelTrainAutoPolicyStatusDto toAutoPolicyStatus(Document policy) {
        if (policy == null) {
            String defaultDatasetKey = resolveDefaultOperationalDatasetKey();
            return new ModelTrainAutoPolicyStatusDto(
                    "",
                    defaultDatasetKey,
                    schemaResolver.buildDatasetLabel(defaultDatasetKey, DEFAULT_DATASET_NAME),
                    DEFAULT_DATASET_NAME,
                    resolveEquipmentId(defaultDatasetKey, null),
                    DEFAULT_SOURCE_COLLECTION,
                    "thisfeature",
                    List.<String>of(),
                    DEFAULT_WINDOW_SIZE,
                    "fixed_count_only",
                    FEATURE_STAT_KEYS,
                    null,
                    0,
                    null,
                    null,
                    null,
                    "TMST_FEATURE_MST",
                    null,
                    ALGO_ISOLATION_FOREST,
                    "Isolation Forest",
                    Map.<String, Object>of(),
                    true,
                    resolveSchedulerIntervalSec(null),
                    Math.max((int) Math.ceil(resolveSchedulerIntervalSec(null) / 60.0D), 1),
                    resolveMinNewFeatureCount(null),
                    resolveMinTotalFeatureCount(null),
                    null,
                    0,
                    0,
                    null,
                    POLICY_STATUS_IDLE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    null
            );
        }

        String policyId = asString(policy.get("policy_id"), "");
        String datasetKey = resolveDatasetKeyForPolicy(
                extractDatasetKeyFromPolicy(policy),
                null,
                null
        );
        String policyAlgoCode = normalizeSupportedAlgoCode(asString(policy.get("algo_code"), ALGO_ISOLATION_FOREST), false);
        FeatureConfigContext featureConfig = resolveFeatureConfigContext(datasetKey, "MODEL_POLICY_STATUS");
        Document datasetConfig = featureConfig.datasetConfig();
        List<String> selectedColumns = resolveFeatureSelectedColumns(featureConfig);
        int windowSize = resolveWindowSizeOrDefault(resolveFeatureWindowSizeOrNull(featureConfig));
        String datasetName = resolveFeatureDatasetName(featureConfig);
        if (datasetName == null) {
            datasetName = DEFAULT_DATASET_NAME;
        }
        String sourceCollection = firstNonBlank(resolveFeatureSourceCollection(featureConfig), DEFAULT_SOURCE_COLLECTION);
        String targetCollection = firstNonBlank(resolveFeatureTargetCollection(featureConfig), "thisfeature");
        String windowMode = firstNonBlank(resolveFeatureWindowMode(featureConfig), "fixed_count_only");
        List<String> featureStats = resolveFeatureStats(featureConfig);
        Boolean featureSchedulerEnabled = resolveFeatureSchedulerEnabled(featureConfig);
        int featureSchedulerIntervalSec = defaultIfNull(resolveFeatureSchedulerIntervalSec(featureConfig), 0);
        String featureLastStatus = resolveFeatureLastStatus(featureConfig);
        String featureLastWindowEnd = resolveFeatureLastWindowEnd(featureConfig);
        String featureLastCheckpointValue = resolveFeatureLastCheckpointValue(featureConfig);
        String equipmentId = resolveEquipmentId(
                datasetKey,
                datasetConfig == null
                        ? null
                        : asString(datasetConfig.get("equipment_id"), normalizeOptionalText(datasetConfig.get(EQUIPMENT_FIELD)))
        );

        Document latestRun = modelTrainRepository.findLatestModelRunByPolicyId(policyId);
        String latestRunId = latestRun == null ? null : asString(latestRun.get("run_id"), null);
        String lastTrainStatus = latestRun == null ? POLICY_STATUS_IDLE : asString(latestRun.get("status"), POLICY_STATUS_IDLE);
        String lastTrainAt = latestRun == null
                ? null
                : normalizeTimestamp(latestRun.get("updated_at")) == null
                ? normalizeTimestamp(latestRun.get("reg_date"))
                : normalizeTimestamp(latestRun.get("updated_at"));

        Object lastTrainWindowEnd = latestRunId == null
                ? null
                : modelTrainRepository.findLatestAnomalyWindowEndByRunId(latestRunId);
        long totalFeatureCount;
        long pendingNewFeatureCount;
        if (ALGO_RANDOM_FOREST.equals(policyAlgoCode)) {
            totalFeatureCount = modelTrainRepository.countLabeledRowsByLabels(datasetKey, List.of(0, 1));
            pendingNewFeatureCount = totalFeatureCount;
            lastTrainWindowEnd = null;
        } else {
            totalFeatureCount = modelTrainRepository.countFeatureRowsByDatasetKey(datasetKey);
            pendingNewFeatureCount = modelTrainRepository.countFeatureRowsByDatasetKeyAfterWindowEnd(
                    datasetKey,
                    lastTrainWindowEnd
            );
        }
        long runHistoryCount = modelTrainRepository.countRunHistoryByPolicyId(policyId);
        Integer recentWindowLimit;
        try {
            recentWindowLimit = resolveRecentWindowLimit(asInteger(policy.get("recent_window_limit")));
        } catch (IllegalArgumentException ignored) {
            recentWindowLimit = null;
        }
        int modelSchedulerIntervalSec = resolvePolicySchedulerIntervalSec(policy);
        Integer modelSchedulerIntervalMinutes = resolvePolicySchedulerIntervalMinutes(policy, modelSchedulerIntervalSec);

        return new ModelTrainAutoPolicyStatusDto(
                policyId,
                datasetKey,
                schemaResolver.buildDatasetLabel(datasetKey, datasetName),
                datasetName,
                equipmentId,
                sourceCollection,
                targetCollection,
                selectedColumns,
                windowSize,
                windowMode,
                featureStats,
                featureSchedulerEnabled,
                featureSchedulerIntervalSec,
                featureLastStatus,
                featureLastWindowEnd,
                featureLastCheckpointValue,
                featureConfig.configSource(),
                featureConfig.configMessage(),
                asString(policy.get("algo_code"), ALGO_ISOLATION_FOREST),
                asString(policy.get("algo_name"), defaultAlgoName(asString(policy.get("algo_code"), ALGO_ISOLATION_FOREST))),
                normalizeParamsFromObject(policy.get("params")),
                asBoolean(policy.get("auto_train_enabled"), true),
                modelSchedulerIntervalSec,
                modelSchedulerIntervalMinutes,
                resolveMinNewFeatureCount(asInteger(policy.get("min_new_feature_count"))),
                resolveMinTotalFeatureCount(asInteger(policy.get("min_total_feature_count"))),
                recentWindowLimit,
                pendingNewFeatureCount,
                totalFeatureCount,
                lastTrainAt,
                lastTrainStatus,
                latestRunId,
                normalizeTimestamp(lastTrainWindowEnd),
                null,
                null,
                normalizeTimestamp(policy.get("updated_at")),
                null,
                normalizeTimestamp(latestRun == null ? null : latestRun.get("updated_at")),
                runHistoryCount,
                normalizeTimestamp(policy.get("updated_at")),
                normalizeTimestamp(policy.get("reg_date"))
        );
    }

    private Document buildRunDocument(
            String runId,
            String datasetKey,
            String equipmentId,
            String sensorId,
            List<String> selectedColumns,
            int windowSize,
            String datasetName,
            String sourceCollection,
            String targetCollection,
            String algoCode,
            String algoName,
            Map<String, Object> params,
            String policyId,
            String triggerType,
            Integer recentWindowLimit,
            String featureConfigSource,
            String featureConfigMessage
    ) {
        Map<String, Object> storedParams = new LinkedHashMap<>(params);
        storedParams.put(INTERNAL_DATASET_KEY_PARAM, datasetKey);
        String normalizedEquipmentId = canonicalizeEquipmentId(equipmentId);
        if (normalizedEquipmentId == null) {
            normalizedEquipmentId = canonicalizeEquipmentId(schemaResolver.resolveEquipmentScopeFromDatasetKey(datasetKey));
        }
        if (normalizedEquipmentId == null) {
            normalizedEquipmentId = "UNKNOWN";
        }
        Date now = new Date();

        Document document = new Document();
        document.put(DOC_TYPE_FIELD, DOC_TYPE_RUN);
        document.put("run_id", runId);
        document.put("policy_id", policyId);
        document.put("trigger_type", triggerType);
        document.put(EQUIPMENT_FIELD, normalizedEquipmentId);
        document.put("equipment_id", normalizedEquipmentId);
        document.put("sensor_id", sensorId);
        document.put("dataset_key", datasetKey);
        document.put("dataset_name", datasetName == null ? DEFAULT_DATASET_NAME : datasetName);
        if (sourceCollection != null) {
            document.put("source_collection", sourceCollection);
        }
        if (targetCollection != null) {
            document.put("target_collection", targetCollection);
        }
        document.put("dataset_key_hash", schemaResolver.datasetKeyHash(datasetKey));
        document.put("selected_columns", selectedColumns);
        document.put("window_size", windowSize);
        document.put("algo_code", algoCode);
        document.put("algo_name", algoName);
        document.put("params", storedParams);
        document.put("recent_window_limit", recentWindowLimit);
        if (featureConfigSource != null) {
            document.put("feature_config_source", featureConfigSource);
        }
        if (featureConfigMessage != null) {
            document.put("feature_config_message", featureConfigMessage);
        }
        document.put("status", READY_STATUS);
        document.put("reg_date", now);
        document.put("updated_at", now);
        return document;
    }

    private List<String> extractSelectedColumnsFromDatasetConfig(Document datasetConfig) {
        if (datasetConfig == null) {
            return List.of();
        }
        Object selectedColumns = datasetConfig.get("selected_columns");
        if (!(selectedColumns instanceof List<?> rawColumns)) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (Object rawColumn : rawColumns) {
            String column = normalizeOptionalText(rawColumn);
            if (column != null) {
                normalized.add(column);
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return normalized.stream().distinct().toList();
    }

    private FeatureConfigContext resolveFeatureConfigContext(String datasetKey, String usageContext) {
        String normalizedDatasetKey = resolveOptionalDatasetKey(datasetKey);
        if (normalizedDatasetKey == null) {
            return new FeatureConfigContext(
                    null,
                    null,
                    null,
                    "TMST_DATASET_CONFIG_FALLBACK",
                    "dataset_key is missing or invalid."
            );
        }

        Document featurePolicy = modelTrainRepository.findActiveFeaturePolicyByDatasetKey(normalizedDatasetKey);
        Document datasetConfig = modelTrainRepository.findDatasetConfigByDatasetKey(normalizedDatasetKey);
        if (featurePolicy != null) {
            return buildFeatureConfigContext(normalizedDatasetKey, featurePolicy, datasetConfig, null);
        }

        // Deprecated compatibility path: keep legacy tmst_dataset_config fields as fallback only.
        String message = "tmst_feature_mst(use_yn='Y') not found for dataset_key="
                + normalizedDatasetKey
                + ". fallback to tmst_dataset_config.";
        log.warn("{} context={}", message, usageContext);
        return buildFeatureConfigContext(normalizedDatasetKey, null, datasetConfig, message);
    }

    private FeatureConfigContext buildFeatureConfigContext(
            String datasetKey,
            Document featurePolicy,
            Document datasetConfig,
            String configMessage
    ) {
        String configSource = featurePolicy == null ? "TMST_DATASET_CONFIG_FALLBACK" : "TMST_FEATURE_MST";
        return new FeatureConfigContext(
                datasetKey,
                featurePolicy,
                datasetConfig,
                configSource,
                configMessage
        );
    }

    private List<String> resolveFeatureSelectedColumns(FeatureConfigContext featureConfig) {
        if (featureConfig == null) {
            return List.of();
        }
        Document featurePolicy = featureConfig.featurePolicy();
        if (featurePolicy != null) {
            return extractStringList(featurePolicy.get("selected_columns"));
        }
        return extractSelectedColumnsFromDatasetConfig(featureConfig.datasetConfig());
    }

    private Integer resolveFeatureWindowSizeOrNull(FeatureConfigContext featureConfig) {
        if (featureConfig == null) {
            return null;
        }
        Integer windowSize;
        if (featureConfig.featurePolicy() != null) {
            windowSize = asInteger(featureConfig.featurePolicy().get("window_size"));
        } else {
            windowSize = featureConfig.datasetConfig() == null
                    ? null
                    : asInteger(featureConfig.datasetConfig().get("window_size"));
        }
        if (windowSize != null && windowSize <= 0) {
            return null;
        }
        return windowSize;
    }

    private String resolveFeatureDatasetName(FeatureConfigContext featureConfig) {
        if (featureConfig == null) {
            return null;
        }
        Document featurePolicy = featureConfig.featurePolicy();
        if (featurePolicy != null) {
            String datasetName = normalizeOptionalText(featurePolicy.get("dataset_name"));
            if (datasetName != null) {
                return datasetName;
            }
            String sourceCollection = normalizeOptionalText(featurePolicy.get("source_collection"));
            if (sourceCollection != null) {
                return sourceCollection;
            }
        }

        Document datasetConfig = featureConfig.datasetConfig();
        if (datasetConfig != null) {
            String datasetName = normalizeOptionalText(datasetConfig.get("dataset_name"));
            if (datasetName != null) {
                return datasetName;
            }
            return normalizeOptionalText(datasetConfig.get("source_collection"));
        }
        return null;
    }

    private String resolveFeatureSourceCollection(FeatureConfigContext featureConfig) {
        if (featureConfig == null) {
            return null;
        }
        Document featurePolicy = featureConfig.featurePolicy();
        String sourceCollection = featurePolicy == null ? null : normalizeOptionalText(featurePolicy.get("source_collection"));
        if (sourceCollection != null) {
            return sourceCollection;
        }
        Document datasetConfig = featureConfig.datasetConfig();
        return datasetConfig == null ? null : normalizeOptionalText(datasetConfig.get("source_collection"));
    }

    private String resolveFeatureTargetCollection(FeatureConfigContext featureConfig) {
        if (featureConfig == null) {
            return null;
        }
        Document featurePolicy = featureConfig.featurePolicy();
        String targetCollection = featurePolicy == null ? null : normalizeOptionalText(featurePolicy.get("target_collection"));
        if (targetCollection != null) {
            return targetCollection;
        }
        Document datasetConfig = featureConfig.datasetConfig();
        return datasetConfig == null ? null : normalizeOptionalText(datasetConfig.get("target_collection"));
    }

    private String resolveFeatureWindowMode(FeatureConfigContext featureConfig) {
        if (featureConfig == null) {
            return null;
        }
        if (featureConfig.featurePolicy() != null) {
            return normalizeOptionalText(featureConfig.featurePolicy().get("window_mode"));
        }
        return featureConfig.datasetConfig() == null
                ? null
                : normalizeOptionalText(featureConfig.datasetConfig().get("window_mode"));
    }

    private List<String> resolveFeatureStats(FeatureConfigContext featureConfig) {
        if (featureConfig == null) {
            return FEATURE_STAT_KEYS;
        }
        List<String> configured;
        if (featureConfig.featurePolicy() != null) {
            configured = extractStringList(featureConfig.featurePolicy().get("feature_stats"));
        } else {
            configured = featureConfig.datasetConfig() == null
                    ? List.of()
                    : extractStringList(featureConfig.datasetConfig().get("feature_stats"));
        }
        if (configured.isEmpty()) {
            return FEATURE_STAT_KEYS;
        }
        return configured;
    }

    private Boolean resolveFeatureSchedulerEnabled(FeatureConfigContext featureConfig) {
        if (featureConfig == null || featureConfig.featurePolicy() == null) {
            return null;
        }
        Object rawValue = featureConfig.featurePolicy().get("scheduler_enabled");
        if (rawValue == null) {
            return null;
        }
        return asBoolean(rawValue, false);
    }

    private Integer resolveFeatureSchedulerIntervalSec(FeatureConfigContext featureConfig) {
        if (featureConfig == null || featureConfig.featurePolicy() == null) {
            return null;
        }
        Integer intervalSec = asInteger(featureConfig.featurePolicy().get("scheduler_interval_sec"));
        if (intervalSec == null || intervalSec <= 0) {
            return null;
        }
        return intervalSec;
    }

    private String resolveFeatureLastStatus(FeatureConfigContext featureConfig) {
        if (featureConfig == null || featureConfig.featurePolicy() == null) {
            return null;
        }
        return normalizeOptionalText(featureConfig.featurePolicy().get("last_status"));
    }

    private String resolveFeatureLastWindowEnd(FeatureConfigContext featureConfig) {
        if (featureConfig == null || featureConfig.featurePolicy() == null) {
            return null;
        }
        return normalizeTimestamp(featureConfig.featurePolicy().get("last_window_end"));
    }

    private String resolveFeatureLastCheckpointValue(FeatureConfigContext featureConfig) {
        if (featureConfig == null || featureConfig.featurePolicy() == null) {
            return null;
        }
        return normalizeOptionalText(featureConfig.featurePolicy().get("last_checkpoint_value"));
    }

    private List<String> extractStringList(Object rawList) {
        if (!(rawList instanceof List<?> listValue)) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object item : listValue) {
            String normalized = normalizeOptionalText(item);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private String extractDatasetKeyFromPolicy(Document policy) {
        if (policy == null) {
            return null;
        }

        String datasetKey = schemaResolver.normalizeDatasetKeyString(policy.get("dataset_key"));
        if (datasetKey != null) {
            return datasetKey;
        }

        return schemaResolver.normalizeDatasetKeyString(policy.get("dataset_name"));
    }

    private String resolveDatasetKeyForPolicy(String datasetKey, String equipmentId, String sensorId) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey != null) {
            return normalizedDatasetKey;
        }

        String fromEquipment = equipmentMasterService.resolveDatasetKeyByEquipment(equipmentId);
        if (fromEquipment != null) {
            return fromEquipment;
        }
        return resolveDefaultOperationalDatasetKey();
    }

    private String resolveOptionalDatasetKey(String datasetKey) {
        if (datasetKey == null || datasetKey.isBlank()) {
            return null;
        }
        return resolveDatasetKeyForPolicy(datasetKey, null, null);
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

    private String resolvePolicyIdForUpsert(String datasetKey, String algoCode) {
        List<Document> matchedPolicies = modelTrainRepository.findPoliciesByDatasetAndAlgo(datasetKey, algoCode);
        if (!matchedPolicies.isEmpty()) {
            Document primaryCandidate = selectPrimaryPolicyCandidate(matchedPolicies);
            String existingPolicyId = primaryCandidate == null ? null : normalizeOptionalText(primaryCandidate.get("policy_id"));
            if (existingPolicyId != null) {
                return existingPolicyId;
            }
        }
        return buildPolicyId(algoCode, datasetKey);
    }

    private Document selectPrimaryPolicyCandidate(List<Document> policies) {
        if (policies == null || policies.isEmpty()) {
            return null;
        }
        return policies.stream()
                .sorted(
                        Comparator.comparing(
                                        this::priorityForPolicyCandidate,
                                        Comparator.naturalOrder()
                                )
                                .thenComparing(
                                        policy -> normalizeOptionalText(policy.get("policy_id")),
                                        Comparator.nullsLast(String::compareToIgnoreCase)
                                )
                                .thenComparing(
                                        policy -> normalizeTimestamp(policy.get("updated_at")),
                                        Comparator.nullsLast(String::compareTo)
                                )
                )
                .findFirst()
                .orElse(null);
    }

    private int priorityForPolicyCandidate(Document policy) {
        String policyId = policy == null ? null : normalizeOptionalText(policy.get("policy_id"));
        if (policyId != null && policyId.toUpperCase(Locale.ROOT).contains("_DEFAULT_")) {
            return 0;
        }
        return 1;
    }

    private String buildPolicyId(String algoCode, String datasetKey) {
        String normalizedAlgoCode = normalizeRequiredText(algoCode, "algo_code").toUpperCase(Locale.ROOT);
        String resolvedDatasetKey = resolveDatasetKeyForPolicy(datasetKey, null, null);
        if (ALGO_RANDOM_FOREST.equals(normalizedAlgoCode)
                && DATASET_THISRAWLABELED_ALL_RF_V1.equalsIgnoreCase(resolvedDatasetKey)) {
            return POLICY_RANDOM_FOREST_SUPERVISED_V1;
        }
        String normalizedDatasetKey = resolveDatasetKeyForPolicy(datasetKey, null, null)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_");
        String datasetSuffix = normalizedDatasetKey.length() > 20
                ? normalizedDatasetKey.substring(0, 20)
                : normalizedDatasetKey;
        return "POLICY_" + normalizedAlgoCode + "_" + datasetSuffix + "_V1";
    }

    private int resolveSchedulerIntervalSec(Integer schedulerIntervalSec) {
        if (schedulerIntervalSec == null) {
            return Math.max(defaultSchedulerIntervalSec, 1);
        }
        if (schedulerIntervalSec <= 0) {
            throw new IllegalArgumentException("scheduler_interval_sec must be greater than 0.");
        }
        return schedulerIntervalSec;
    }

    private int resolveSchedulerIntervalMinutes(Integer schedulerIntervalMinutes) {
        if (schedulerIntervalMinutes == null) {
            return Math.max((int) Math.ceil(Math.max(defaultSchedulerIntervalSec, 1) / 60.0D), 1);
        }
        if (schedulerIntervalMinutes <= 0) {
            throw new IllegalArgumentException("scheduler_interval_minutes must be greater than 0.");
        }
        return schedulerIntervalMinutes;
    }

    private int resolvePolicySchedulerIntervalSec(Document policy) {
        if (policy == null) {
            return resolveSchedulerIntervalSec(null);
        }
        Integer schedulerIntervalMinutes = asInteger(policy.get("scheduler_interval_minutes"));
        if (schedulerIntervalMinutes != null) {
            return Math.max(resolveSchedulerIntervalMinutes(schedulerIntervalMinutes) * 60, 1);
        }
        return resolveSchedulerIntervalSec(asInteger(policy.get("scheduler_interval_sec")));
    }

    private Integer resolvePolicySchedulerIntervalMinutes(Document policy, int schedulerIntervalSec) {
        if (policy != null) {
            Integer schedulerIntervalMinutes = asInteger(policy.get("scheduler_interval_minutes"));
            if (schedulerIntervalMinutes != null && schedulerIntervalMinutes > 0) {
                return resolveSchedulerIntervalMinutes(schedulerIntervalMinutes);
            }
        }
        if (schedulerIntervalSec <= 0) {
            return null;
        }
        return Math.max((int) Math.ceil(schedulerIntervalSec / 60.0D), 1);
    }

    private int resolveMinNewFeatureCount(Integer minNewFeatureCount) {
        if (minNewFeatureCount == null) {
            return Math.max(defaultMinNewFeatureCount, 0);
        }
        if (minNewFeatureCount < 0) {
            throw new IllegalArgumentException("min_new_feature_count must be greater than or equal to 0.");
        }
        return minNewFeatureCount;
    }

    private int resolveMinTotalFeatureCount(Integer minTotalFeatureCount) {
        if (minTotalFeatureCount == null) {
            return Math.max(defaultMinTotalFeatureCount, 1);
        }
        if (minTotalFeatureCount <= 0) {
            throw new IllegalArgumentException("min_total_feature_count must be greater than 0.");
        }
        return minTotalFeatureCount;
    }

    private Integer resolveRecentWindowLimit(Integer recentWindowLimit) {
        if (recentWindowLimit == null) {
            return null;
        }
        if (recentWindowLimit <= 0) {
            throw new IllegalArgumentException("recent_window_limit must be greater than 0.");
        }
        return recentWindowLimit;
    }

    private boolean isPolicyEnabledForAutoTrain(Document policy) {
        if (policy == null) {
            return false;
        }

        String useFlag = normalizeOptionalText(policy.get("use_flag"));
        if (useFlag != null && !ACTIVE_USE_FLAG.equalsIgnoreCase(useFlag)) {
            return false;
        }
        return asBoolean(policy.get("auto_train_enabled"), true);
    }

    private boolean isRandomForestPolicy(Document policy) {
        if (policy == null) {
            return false;
        }
        String algoCode = normalizeSupportedAlgoCode(asString(policy.get("algo_code"), null), false);
        return ALGO_RANDOM_FOREST.equals(algoCode);
    }

    private void upsertActiveSelection(String datasetKey, Document policy, String changedReason) {
        if (policy == null) {
            return;
        }

        String normalizedDatasetKey = resolveDatasetKeyForPolicy(datasetKey, null, null);
        String policyId = normalizeOptionalText(policy.get("policy_id"));
        String algoCode = normalizeOptionalText(policy.get("algo_code"));
        if (policyId == null || algoCode == null) {
            return;
        }

        Map<String, Object> activeFields = new LinkedHashMap<>();
        activeFields.put("active_policy_id", policyId);
        activeFields.put("active_algo_code", algoCode);
        activeFields.put("active_algo_name", asString(policy.get("algo_name"), defaultAlgoName(algoCode)));
        activeFields.put("changed_by", "system");
        activeFields.put("changed_reason", normalizeOptionalText(changedReason) == null ? "SYSTEM_UPDATE" : changedReason);
        activeFields.put("use_flag", ACTIVE_USE_FLAG);
        activeFields.put("updated_at", new Date());

        try {
            modelTrainRepository.upsertModelActive(normalizedDatasetKey, activeFields);
        } catch (Exception exception) {
            log.error(
                    "Failed to upsert tmst_model_active while saving model policy. dataset_key={}, policy_id={}",
                    normalizedDatasetKey,
                    policyId,
                    exception
            );
        }
    }

    private Object resolveLastTrainedWindowEnd(String policyId) {
        Document latestRun = modelTrainRepository.findLatestModelRunByPolicyId(policyId);
        if (latestRun == null) {
            return null;
        }
        String latestRunId = normalizeOptionalText(latestRun.get("run_id"));
        if (latestRunId == null) {
            return null;
        }
        return modelTrainRepository.findLatestAnomalyWindowEndByRunId(latestRunId);
    }

    private int resolveWindowSizeOrDefault(Integer windowSize) {
        try {
            return resolveWindowSize(windowSize);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_WINDOW_SIZE;
        }
    }

    private String asString(Object value, String fallback) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? fallback : normalized;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private long asLong(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return 0L;
            }
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
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
        String normalized = normalizeOptionalText(value);
        return normalized;
    }

    private Instant parseInstantOrNull(Object value) {
        String normalizedTimestamp = normalizeTimestamp(value);
        if (normalizedTimestamp == null) {
            return null;
        }
        try {
            return Instant.parse(normalizedTimestamp);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void logSchedulerDueDecision(
            String datasetKey,
            String activePolicyId,
            Instant lastRunTime,
            Instant now,
            int intervalMinutes,
            Long diffMillis,
            String reason
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "Model-train scheduler due evaluation. dataset_key={}, active_policy_id={}, lastRunTime={}, now={}, intervalMinutes={}, diffMillis={}, skipReason={}",
                datasetKey,
                activePolicyId,
                lastRunTime,
                now,
                intervalMinutes,
                diffMillis,
                reason
        );
    }

    private List<String> normalizeSelectedColumns(List<String> selectedColumns) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            throw new IllegalArgumentException("selected_columns must not be empty.");
        }

        List<String> normalized = selectedColumns.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("selected_columns must not be empty.");
        }

        return normalized;
    }

    private Map<String, Object> normalizeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            normalized.put(key.trim(), normalizeParamValue(entry.getValue()));
        }
        return normalized;
    }

    private Map<String, Object> resolvePolicyParamsSnapshot(String algoCode, Map<String, Object> requestParams) {
        Map<String, Object> normalizedRequestParams = normalizeParams(requestParams);
        if (normalizedRequestParams.isEmpty()) {
            throw new IllegalArgumentException("params must not be empty.");
        }

        Map<String, Object> resolvedParams = new LinkedHashMap<>(normalizedRequestParams);
        List<AlgorithmParamDto> templateParams = paramRepository.findActiveParamsByAlgoCd(algoCode);

        if (!templateParams.isEmpty()) {
            for (AlgorithmParamDto templateParam : templateParams) {
                String paramCode = normalizeOptionalText(templateParam.paramCd());
                if (paramCode == null) {
                    continue;
                }

                if (isEmptyParamValue(resolvedParams.get(paramCode))) {
                    Object templateDefaultValue = normalizeParamValue(templateParam.defaultValue());
                    if (!isEmptyParamValue(templateDefaultValue)) {
                        resolvedParams.put(paramCode, templateDefaultValue);
                    }
                }

                if (isRequiredParam(templateParam.requiredYn()) && isEmptyParamValue(resolvedParams.get(paramCode))) {
                    throw new IllegalArgumentException(
                            "Required parameter is missing and has no default value. algo_code="
                                    + algoCode
                                    + ", param_cd="
                                    + paramCode
                    );
                }
            }
        }

        applyHardcodedParamFallback(algoCode, resolvedParams);

        if (resolvedParams.isEmpty()) {
            throw new IllegalArgumentException("params must not be empty.");
        }
        return resolvedParams;
    }

    private void applyHardcodedParamFallback(String algoCode, Map<String, Object> params) {
        if (ALGO_RANDOM_FOREST.equals(algoCode)) {
            putFallbackIfMissing(params, PARAM_N_ESTIMATORS, RF_DEFAULT_N_ESTIMATORS);
            putFallbackIfMissing(params, PARAM_MAX_DEPTH, RF_DEFAULT_MAX_DEPTH);
            putFallbackIfMissing(params, PARAM_MIN_SAMPLES_SPLIT, RF_DEFAULT_MIN_SAMPLES_SPLIT);
            putFallbackIfMissing(params, PARAM_MIN_SAMPLES_LEAF, RF_DEFAULT_MIN_SAMPLES_LEAF);
            putFallbackIfMissing(params, PARAM_MAX_FEATURES, RF_DEFAULT_MAX_FEATURES);
            putFallbackIfMissing(params, PARAM_CLASS_WEIGHT, RF_DEFAULT_CLASS_WEIGHT);
            putFallbackIfMissing(params, PARAM_TRAIN_VALID_RATIO, RF_DEFAULT_TRAIN_VALID_RATIO);
            putFallbackIfMissing(params, PARAM_SEED, RF_DEFAULT_SEED);
            putFallbackIfMissing(params, PARAM_EARLY_STOPPING, false);
            putFallbackIfMissing(params, PARAM_LABEL_FIELD, RF_DEFAULT_LABEL_FIELD);
            putFallbackIfMissing(params, PARAM_LABEL_VERSION, RF_DEFAULT_LABEL_VERSION);
            putFallbackIfMissing(params, PARAM_TRAIN_SAMPLE_LIMIT, Math.max(defaultSupervisedSampleLimit, 1));
            return;
        }

        if (ALGO_ISOLATION_FOREST.equals(algoCode)) {
            putFallbackIfMissing(params, PARAM_CONTAMINATION, IF_DEFAULT_CONTAMINATION);
            putFallbackIfMissing(params, PARAM_N_ESTIMATORS, IF_DEFAULT_N_ESTIMATORS);
            putFallbackIfMissing(params, PARAM_MAX_SAMPLES, IF_DEFAULT_MAX_SAMPLES);
            putFallbackIfMissing(params, PARAM_SEED, IF_DEFAULT_SEED);
            return;
        }

        if (ALGO_AUTOENCODER.equals(algoCode)) {
            putFallbackIfMissing(params, PARAM_EPOCH, AE_DEFAULT_EPOCH);
            putFallbackIfMissing(params, PARAM_LEARNING_RATE, AE_DEFAULT_LEARNING_RATE);
            putFallbackIfMissing(params, PARAM_TRAIN_VALID_RATIO, AE_DEFAULT_TRAIN_VALID_RATIO);
            putFallbackIfMissing(params, PARAM_EARLY_STOPPING, AE_DEFAULT_EARLY_STOPPING);
            putFallbackIfMissing(params, PARAM_PATIENCE, AE_DEFAULT_PATIENCE);
            putFallbackIfMissing(params, PARAM_CONTAMINATION, AE_DEFAULT_CONTAMINATION);
            putFallbackIfMissing(params, PARAM_SEED, AE_DEFAULT_SEED);
        }
    }

    private void putFallbackIfMissing(Map<String, Object> params, String paramCode, Object fallbackValue) {
        if (isEmptyParamValue(params.get(paramCode)) && !isEmptyParamValue(fallbackValue)) {
            params.put(paramCode, fallbackValue);
        }
    }

    private boolean isRequiredParam(String requiredYn) {
        return requiredYn != null && REQUIRED_YN.equalsIgnoreCase(requiredYn.trim());
    }

    private boolean isEmptyParamValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String stringValue) {
            return stringValue.trim().isEmpty();
        }
        return false;
    }

    private Object normalizeParamValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String textValue) {
            String trimmed = textValue.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> normalizedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                normalizedMap.put(String.valueOf(entry.getKey()), normalizeParamValue(entry.getValue()));
            }
            return normalizedMap;
        }
        if (value instanceof List<?> listValue) {
            List<Object> normalizedList = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                normalizedList.add(normalizeParamValue(item));
            }
            return normalizedList;
        }
        return value;
    }

    private List<String> extractSelectedColumns(Object featureValuesObject) {
        if (!(featureValuesObject instanceof Map<?, ?> featureValuesMap)) {
            return List.of();
        }

        List<String> extractedColumns = new ArrayList<>();
        for (String statKey : FEATURE_STAT_KEYS) {
            Object statValues = featureValuesMap.get(statKey);
            if (!(statValues instanceof Map<?, ?> statMap)) {
                continue;
            }

            for (Object rawKey : statMap.keySet()) {
                if (rawKey == null) {
                    continue;
                }

                String key = rawKey.toString().trim();
                if (!key.isEmpty()) {
                    extractedColumns.add(key);
                }
            }

            if (!extractedColumns.isEmpty()) {
                break;
            }
        }

        return extractedColumns.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private int resolveDatasetWindowSize(String datasetKey, Object windowStart, Object windowEnd) {
        int countedRows = modelTrainRepository.countRawRowsInWindow(datasetKey, windowStart, windowEnd);
        if (countedRows <= 0) {
            return DEFAULT_WINDOW_SIZE;
        }
        return countedRows;
    }

    private List<String> normalizeSelectedColumnsFromObject(Object selectedColumnsObject) {
        if (!(selectedColumnsObject instanceof List<?> selectedColumnsList)) {
            throw new IllegalArgumentException("selected_columns must not be empty.");
        }

        List<String> selectedColumns = new ArrayList<>();
        for (Object selectedColumn : selectedColumnsList) {
            if (selectedColumn == null) {
                continue;
            }
            String normalized = selectedColumn.toString().trim();
            if (!normalized.isEmpty()) {
                selectedColumns.add(normalized);
            }
        }

        if (selectedColumns.isEmpty()) {
            throw new IllegalArgumentException("selected_columns must not be empty.");
        }

        return selectedColumns.stream().distinct().toList();
    }

    private Map<String, Object> normalizeParamsFromObject(Object paramsObject) {
        if (!(paramsObject instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toString().trim();
            if (key.isEmpty()) {
                continue;
            }
            params.put(key, normalizeParamValue(entry.getValue()));
        }
        return params;
    }

    private List<WindowInputRow> buildWindowInputRows(List<Document> featureRows, List<String> selectedColumns) {
        List<WindowInputRow> rows = new ArrayList<>();
        List<String> expectedFeatureKeyOrder = null;
        List<String> requiredFeatureKeys = null;

        for (Document featureRow : featureRows) {
            Object windowStart = featureRow.get("window_start");
            Object windowEnd = featureRow.get("window_end");
            if (windowStart == null || windowEnd == null) {
                continue;
            }

            List<String> featureStatKeys = resolveFeatureStatKeys(featureRow.get("feature_values"));
            if (requiredFeatureKeys == null) {
                requiredFeatureKeys = buildRequiredFeatureKeys(selectedColumns, featureStatKeys);
            }

            Map<String, Object> inputFeatures = flattenFeatureValues(
                    featureRow.get("feature_values"),
                    selectedColumns,
                    featureStatKeys
            );
            if (inputFeatures.isEmpty()) {
                continue;
            }

            if (!inputFeatures.keySet().containsAll(requiredFeatureKeys)) {
                log.warn("Skipping feature row due to missing input feature keys. missing={}", requiredFeatureKeys.stream()
                        .filter(required -> !inputFeatures.containsKey(required))
                        .toList());
                continue;
            }

            List<String> actualFeatureKeyOrder = new ArrayList<>(inputFeatures.keySet());
            if (expectedFeatureKeyOrder == null) {
                expectedFeatureKeyOrder = List.copyOf(actualFeatureKeyOrder);
            } else if (!expectedFeatureKeyOrder.equals(actualFeatureKeyOrder)) {
                log.warn(
                        "Skipping feature row due to feature-key order mismatch. expected={}, actual={}",
                        expectedFeatureKeyOrder,
                        actualFeatureKeyOrder
                );
                continue;
            }

            rows.add(new WindowInputRow(
                    normalizeOptionalText(featureRow.get("lot_no")),
                    windowStart,
                    windowEnd,
                    inputFeatures
            ));
        }

        return rows;
    }

    private List<String> buildRequiredFeatureKeys(List<String> selectedColumns, List<String> featureStatKeys) {
        List<String> keys = new ArrayList<>(selectedColumns.size() * featureStatKeys.size());
        for (String statKey : featureStatKeys) {
            String normalizedStatKey = statKey.toLowerCase(Locale.ROOT);
            for (String selectedColumn : selectedColumns) {
                keys.add(selectedColumn + "_" + normalizedStatKey);
            }
        }
        return List.copyOf(keys);
    }

    private List<String> resolveFeatureStatKeys(Object featureValuesObject) {
        if (!(featureValuesObject instanceof Map<?, ?> featureValuesMap)) {
            return FEATURE_STAT_KEYS;
        }

        Object metaObject = featureValuesMap.get("META");
        if (!(metaObject instanceof Map<?, ?> metaMap)) {
            return FEATURE_STAT_KEYS;
        }

        Object featureStatsObject = metaMap.get("feature_stats");
        if (featureStatsObject == null) {
            return FEATURE_STAT_KEYS;
        }

        LinkedHashSet<String> normalizedStats = new LinkedHashSet<>();
        if (featureStatsObject instanceof List<?> featureStatsList) {
            for (Object featureStat : featureStatsList) {
                String normalized = normalizeFeatureStatKey(featureStat);
                if (normalized != null) {
                    normalizedStats.add(normalized);
                }
            }
        } else if (featureStatsObject instanceof String featureStatsText) {
            for (String featureStat : featureStatsText.split(",")) {
                String normalized = normalizeFeatureStatKey(featureStat);
                if (normalized != null) {
                    normalizedStats.add(normalized);
                }
            }
        }

        if (normalizedStats.isEmpty()) {
            return FEATURE_STAT_KEYS;
        }
        if (!normalizedStats.containsAll(FEATURE_STAT_KEYS)) {
            return FEATURE_STAT_KEYS;
        }
        return List.copyOf(normalizedStats);
    }

    private String normalizeFeatureStatKey(Object featureStatKeyObject) {
        if (featureStatKeyObject == null) {
            return null;
        }
        String normalized = featureStatKeyObject.toString().trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || !FEATURE_STAT_KEYS.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private Map<String, Object> flattenFeatureValues(
            Object featureValuesObject,
            List<String> selectedColumns,
            List<String> featureStatKeys
    ) {
        if (!(featureValuesObject instanceof Map<?, ?> featureValuesMap)) {
            return Map.of();
        }

        Map<String, Object> flattened = new LinkedHashMap<>();

        for (String statKey : featureStatKeys) {
            Object statValuesObject = featureValuesMap.get(statKey);
            if (!(statValuesObject instanceof Map<?, ?> statValuesMap)) {
                continue;
            }

            String normalizedStatKey = statKey.toLowerCase(Locale.ROOT);
            for (String selectedColumn : selectedColumns) {
                Object rawValue = statValuesMap.get(selectedColumn);
                Double numericValue = toDouble(rawValue);
                if (numericValue == null) {
                    continue;
                }

                flattened.put(selectedColumn + "_" + normalizedStatKey, numericValue);
            }
        }

        return flattened;
    }

    private List<Document> buildAnomalyRows(
            String runId,
            String datasetKey,
            String equipmentId,
            String algoCode,
            List<WindowInputRow> windowInputRows,
            List<AiInferenceResult> inferenceResults
    ) {
        String normalizedAlgoCode = normalizeSupportedAlgoCode(algoCode, true);
        List<Double> anomalyScores = inferenceResults.stream()
                .map(AiInferenceResult::anomalyScore)
                .filter(Objects::nonNull)
                .toList();
        ScoreDistributionStats scoreStats = buildScoreDistributionStats(anomalyScores);

        if (log.isDebugEnabled()) {
            log.debug(
                    "Anomaly health calibration stats (execute). run_id={}, dataset_key={}, algo_code={}, count={}, min={}, max={}, mean={}, stddev={}",
                    runId,
                    datasetKey,
                    normalizedAlgoCode,
                    scoreStats.count(),
                    scoreStats.min(),
                    scoreStats.max(),
                    scoreStats.mean(),
                    scoreStats.stdDev()
            );
        }

        List<Document> anomalyRows = new ArrayList<>(windowInputRows.size());
        HealthCalibrationResult latestCalibrationResult = null;
        Double latestRawScore = null;
        Boolean latestIsAnomaly = null;

        for (int index = 0; index < windowInputRows.size(); index++) {
            WindowInputRow row = windowInputRows.get(index);
            AiInferenceResult inferenceResult = inferenceResults.get(index);

            double anomalyScore = inferenceResult.anomalyScore() == null ? 0D : inferenceResult.anomalyScore();
            boolean isAnomaly = Boolean.TRUE.equals(inferenceResult.isAnomaly());

            HealthCalibrationResult fallbackCalibration =
                    evaluateHealthByAlgorithm(normalizedAlgoCode, anomalyScore, isAnomaly, scoreStats);

            Double aiHealthIndex = inferenceResult.healthIndex();
            String aiStatus = normalizeOptionalText(inferenceResult.status());

            double healthIndex = aiHealthIndex != null && Double.isFinite(aiHealthIndex)
                    ? clampUnitRange(aiHealthIndex)
                    : fallbackCalibration.healthIndex();

            String status = aiStatus != null
                    ? aiStatus.toUpperCase(Locale.ROOT)
                    : fallbackCalibration.status();
            Date windowStartDate = toBsonDate(row.windowStart(), "window_start");
            Date windowEndDate = toBsonDate(row.windowEnd(), "window_end");

            if (windowStartDate != null
                    && windowEndDate != null
                    && windowStartDate.after(windowEndDate)) {

                log.warn(
                        "Window range reversed detected. runId={}, datasetKey={}, start={}, end={}",
                        runId,
                        datasetKey,
                        windowStartDate,
                        windowEndDate
                );

                Date temp = windowStartDate;
                windowStartDate = windowEndDate;
                windowEndDate = temp;
            }
            Date now = new Date();

            Document anomalyRow = new Document();
            anomalyRow.put("run_id", runId);
            anomalyRow.put("dataset_key", datasetKey);
            String normalizedEquipmentId = canonicalizeEquipmentId(equipmentId);
            if (normalizedEquipmentId == null) {
                normalizedEquipmentId = resolveEquipmentId(datasetKey, null);
            }
            if (normalizedEquipmentId == null) {
                normalizedEquipmentId = canonicalizeEquipmentId(schemaResolver.resolveEquipmentScopeFromDatasetKey(datasetKey));
            }
            if (normalizedEquipmentId == null) {
                normalizedEquipmentId = "UNKNOWN";
            }
            anomalyRow.put(EQUIPMENT_FIELD, normalizedEquipmentId);
            anomalyRow.put("equipment_id", normalizedEquipmentId);
            if (row.lotNo() != null) {
                anomalyRow.put("lot_no", row.lotNo());
            }
            anomalyRow.put("window_start", windowStartDate);
            anomalyRow.put("window_end", windowEndDate);
            anomalyRow.put("input_features", row.inputFeatures());
            anomalyRow.put("anomaly_score", anomalyScore);
            anomalyRow.put("is_anomaly", isAnomaly);
            anomalyRow.put("health_index", healthIndex);
            anomalyRow.put("status", status);
            anomalyRow.put("cause_generated", false);
            anomalyRow.put("alert_generated", false);
            anomalyRow.put("reg_date", now);
            anomalyRow.put("updated_at", now);

            anomalyRows.add(anomalyRow);
            latestCalibrationResult = fallbackCalibration;
            latestRawScore = anomalyScore;
            latestIsAnomaly = isAnomaly;
        }

        if (log.isDebugEnabled() && !anomalyRows.isEmpty()) {
            Document latestRow = anomalyRows.get(anomalyRows.size() - 1);
            log.debug(
                    "Anomaly health calibration latest (execute). run_id={}, algo_code={}, raw_score={}, health_index={}, status={}, is_anomaly={}, risk_score={}, calibration_mode={}",
                    runId,
                    normalizedAlgoCode,
                    latestRawScore == null ? latestRow.get("anomaly_score") : latestRawScore,
                    latestRow.get("health_index"),
                    latestRow.get("status"),
                    latestIsAnomaly == null ? latestRow.get("is_anomaly") : latestIsAnomaly,
                    latestCalibrationResult == null ? null : latestCalibrationResult.riskScore(),
                    latestCalibrationResult == null ? null : latestCalibrationResult.mode()
            );
        }

        return anomalyRows;
    }

    private Date toBsonDate(Object value, String fieldName) {
        if (value instanceof Date dateValue) {
            return dateValue;
        }
        if (value instanceof Instant instantValue) {
            return Date.from(instantValue);
        }
        if (value instanceof String textValue) {
            String trimmed = textValue.trim();
            if (!trimmed.isEmpty()) {
                try {
                    return Date.from(Instant.parse(trimmed));
                } catch (Exception ignored) {
                    // Intentionally ignored: fall through to explicit exception for invalid format.
                }
            }
        }
        throw new IllegalStateException(fieldName + " must be Date/Instant/ISO-8601 string. value=" + value);
    }

    private List<AiInferenceResult> executeInferenceInBatches(
            String runId,
            String equipmentId,
            String datasetKey,
            String algoCode,
            List<WindowInputRow> windowInputRows,
            ParameterMappingResult parameterMappingResult
    ) {
        List<AiInferenceResult> allResults = new ArrayList<>(windowInputRows.size());

        for (int startIndex = 0; startIndex < windowInputRows.size(); startIndex += AI_EXECUTE_BATCH_SIZE) {
            int endIndex = Math.min(startIndex + AI_EXECUTE_BATCH_SIZE, windowInputRows.size());
            List<WindowInputRow> batchRows = windowInputRows.subList(startIndex, endIndex);

            Map<String, Object> aiPayload = new LinkedHashMap<>();
            aiPayload.put("algorithm", algoCode);
            aiPayload.put("model_params", parameterMappingResult.modelParams());
            aiPayload.put("execution_meta", buildExecutionMeta(runId, equipmentId, datasetKey, algoCode, parameterMappingResult));
            aiPayload.put("rows", batchRows.stream().map(WindowInputRow::toAiRowPayload).toList());

            List<AiInferenceResult> batchResults = executeAiInference(algoCode, aiPayload);
            if (batchResults.size() != batchRows.size()) {
                throw new IllegalStateException(
                        "AI server result count does not match feature window batch size. run_id="
                                + runId
                                + ", algo_code="
                                + algoCode
                                + ", expected="
                                + batchRows.size()
                                + ", actual="
                                + batchResults.size()
                                + ", startIndex="
                                + startIndex
                                + ", endIndex="
                                + endIndex
                );
            }

            allResults.addAll(batchResults);
        }

        return allResults;
    }

    private List<AiInferenceResult> executeAiInference(String algoCode, Map<String, Object> aiPayload) {
        if (ALGO_ISOLATION_FOREST.equals(algoCode)) {
            return aiModelExecutionClient.executeIsolationForest(aiPayload);
        }
        if (ALGO_AUTOENCODER.equals(algoCode)) {
            return aiModelExecutionClient.executeAutoEncoder(aiPayload);
        }
        throw new IllegalArgumentException("Unsupported algo_code for AI execution: " + algoCode);
    }

    private ParameterMappingResult mapExecutionParameters(String algoCode, Map<String, Object> params) {
        if (ALGO_ISOLATION_FOREST.equals(algoCode)) {
            return mapIsolationForestExecutionParameters(params);
        }
        if (ALGO_AUTOENCODER.equals(algoCode)) {
            return mapAutoEncoderExecutionParameters(params);
        }
        throw new IllegalArgumentException("Unsupported algo_code for parameter mapping: " + algoCode);
    }

    private ParameterMappingResult mapIsolationForestExecutionParameters(Map<String, Object> params) {
        Map<String, Object> modelParams = new LinkedHashMap<>();

        Double contamination = toDouble(params.get(PARAM_CONTAMINATION));
        if (contamination != null) {
            modelParams.put("contamination", contamination);
        }

        Integer nEstimators = toInteger(params.get(PARAM_N_ESTIMATORS));
        if (nEstimators != null) {
            modelParams.put("n_estimators", nEstimators);
        }

        Object maxSamples = normalizeMaxSamples(params.get(PARAM_MAX_SAMPLES));
        if (maxSamples != null) {
            modelParams.put("max_samples", maxSamples);
        }

        Integer randomState = toInteger(params.get(PARAM_SEED));
        if (randomState != null) {
            modelParams.put("random_state", randomState);
        }

        Set<String> directModelParamKeys = Set.of(PARAM_CONTAMINATION, PARAM_N_ESTIMATORS, PARAM_MAX_SAMPLES, PARAM_SEED);
        List<String> metaOnlyParamKeys = new ArrayList<>();
        for (String metaKey : List.of(PARAM_HYPERPARAM_OPT_METHOD, PARAM_TRAIN_VALID_RATIO, PARAM_RETRAIN_CYCLE)) {
            if (params.containsKey(metaKey)) {
                metaOnlyParamKeys.add(metaKey);
            }
        }

        for (String paramKey : params.keySet()) {
            if (paramKey.startsWith("_")) {
                continue;
            }
            if (!directModelParamKeys.contains(paramKey) && !metaOnlyParamKeys.contains(paramKey)) {
                metaOnlyParamKeys.add(paramKey);
            }
        }

        return new ParameterMappingResult(
                modelParams,
                params,
                metaOnlyParamKeys
        );
    }

    private ParameterMappingResult mapAutoEncoderExecutionParameters(Map<String, Object> params) {
        Map<String, Object> modelParams = new LinkedHashMap<>();

        Integer hiddenUnits = toInteger(params.get(PARAM_HIDDEN_UNITS));
        if (hiddenUnits != null) {
            modelParams.put("hidden_units", hiddenUnits);
        }

        Integer latentDim = toInteger(params.get(PARAM_LATENT_DIM));
        if (latentDim != null) {
            modelParams.put("latent_dim", latentDim);
        }

        Integer batchSize = toInteger(params.get(PARAM_BATCH_SIZE));
        if (batchSize != null) {
            modelParams.put("batch_size", batchSize);
        }

        Integer epoch = toInteger(params.get(PARAM_EPOCH));
        if (epoch != null) {
            modelParams.put("epoch", epoch);
        }

        Double learningRate = toDouble(params.get(PARAM_LEARNING_RATE));
        if (learningRate != null) {
            modelParams.put("learning_rate", learningRate);
        }

        Double trainValidRatio = toDouble(params.get(PARAM_TRAIN_VALID_RATIO));
        if (trainValidRatio != null) {
            modelParams.put("train_valid_ratio", trainValidRatio);
        }

        Boolean earlyStopping = toBoolean(params.get(PARAM_EARLY_STOPPING));
        if (earlyStopping != null) {
            modelParams.put("early_stopping", earlyStopping);
        }

        Integer patience = toInteger(params.get(PARAM_PATIENCE));
        if (patience != null) {
            modelParams.put("patience", patience);
        }

        Double contamination = toDouble(params.get(PARAM_CONTAMINATION));
        if (contamination != null) {
            modelParams.put("contamination", contamination);
        }

        Integer seed = toInteger(params.get(PARAM_SEED));
        if (seed != null) {
            modelParams.put("seed", seed);
        }

        Set<String> directModelParamKeys = Set.of(
                PARAM_HIDDEN_UNITS,
                PARAM_LATENT_DIM,
                PARAM_BATCH_SIZE,
                PARAM_EPOCH,
                PARAM_LEARNING_RATE,
                PARAM_TRAIN_VALID_RATIO,
                PARAM_EARLY_STOPPING,
                PARAM_PATIENCE,
                PARAM_CONTAMINATION,
                PARAM_SEED
        );
        Set<String> reservedParamKeys = Set.of(PARAM_SEQUENCE_LENGTH, PARAM_DROPOUT);
        List<String> metaOnlyParamKeys = new ArrayList<>();

        for (String reservedParamKey : reservedParamKeys) {
            if (params.containsKey(reservedParamKey)) {
                metaOnlyParamKeys.add(reservedParamKey);
            }
        }
        for (String metaKey : List.of(PARAM_HYPERPARAM_OPT_METHOD, PARAM_RETRAIN_CYCLE)) {
            if (params.containsKey(metaKey)) {
                metaOnlyParamKeys.add(metaKey);
            }
        }

        for (String paramKey : params.keySet()) {
            if (paramKey.startsWith("_")) {
                continue;
            }
            if (!directModelParamKeys.contains(paramKey) && !metaOnlyParamKeys.contains(paramKey)) {
                metaOnlyParamKeys.add(paramKey);
            }
        }

        return new ParameterMappingResult(
                modelParams,
                params,
                metaOnlyParamKeys
        );
    }

    private Map<String, Object> buildExecutionMeta(
            String runId,
            String equipmentId,
            String datasetKey,
            String algoCode,
            ParameterMappingResult parameterMappingResult
    ) {
        Map<String, Object> executionMeta = new LinkedHashMap<>();
        executionMeta.put("run_id", runId);
        executionMeta.put(EQUIPMENT_FIELD, equipmentId);
        executionMeta.put("equipment_id", equipmentId);
        executionMeta.put("dataset_key", datasetKey);
        executionMeta.put("algo_code", algoCode);
        executionMeta.put("meta_only_param_keys", parameterMappingResult.metaOnlyParamKeys());

        String inferredSensorId = resolveSensorId(datasetKey, null);
        if (inferredSensorId != null
                && !"all".equalsIgnoreCase(inferredSensorId)
                && !"default".equalsIgnoreCase(inferredSensorId)
                && !DEFAULT_SOURCE_COLLECTION.equalsIgnoreCase(inferredSensorId)) {
            executionMeta.put("sensor_id", inferredSensorId);
        }
        if (parameterMappingResult.originalParams().containsKey(PARAM_HYPERPARAM_OPT_METHOD)) {
            executionMeta.put("hyperparam_opt_method", parameterMappingResult.originalParams().get(PARAM_HYPERPARAM_OPT_METHOD));
        }
        if (parameterMappingResult.originalParams().containsKey(PARAM_TRAIN_VALID_RATIO)) {
            executionMeta.put("train_valid_ratio", parameterMappingResult.originalParams().get(PARAM_TRAIN_VALID_RATIO));
        }
        if (parameterMappingResult.originalParams().containsKey(PARAM_RETRAIN_CYCLE)) {
            executionMeta.put("retrain_cycle", parameterMappingResult.originalParams().get(PARAM_RETRAIN_CYCLE));
        }
        if (ALGO_AUTOENCODER.equals(algoCode)) {
            executionMeta.put("input_mode", "DENSE_ROW_WISE");
            if (parameterMappingResult.originalParams().containsKey(PARAM_SEQUENCE_LENGTH)) {
                executionMeta.put("sequence_length_mode", "RESERVED_NOT_USED_IN_DENSE_V1");
            }
            if (parameterMappingResult.originalParams().containsKey(PARAM_DROPOUT)) {
                executionMeta.put("dropout_mode", "RESERVED_NOT_USED_IN_DENSE_V1");
            }
        }

        return executionMeta;
    }

    private String extractDatasetKeyFromModelRun(Document modelRun) {
        String datasetKey = schemaResolver.normalizeDatasetKeyString(modelRun.get("dataset_key"));
        if (datasetKey != null) {
            return datasetKey;
        }

        Object paramsObject = modelRun.get("params");
        if (paramsObject instanceof Map<?, ?> paramsMap) {
            Object rawDatasetKeyObject = paramsMap.get(INTERNAL_DATASET_KEY_PARAM);
            String internalDatasetKey = schemaResolver.normalizeDatasetKeyString(rawDatasetKeyObject);
            if (internalDatasetKey != null) {
                return internalDatasetKey;
            }
        }

        return resolveDatasetKeyForPolicy(
                datasetKey,
                asString(modelRun.get("equipment_id"), normalizeOptionalText(modelRun.get(EQUIPMENT_FIELD))),
                normalizeOptionalText(modelRun.get("sensor_id"))
        );
    }

    private String resolveEquipmentId(String datasetKey, Object fallbackEquipmentId) {
        String fromFallback = canonicalizeEquipmentId(fallbackEquipmentId);
        if (fromFallback != null) {
            return fromFallback;
        }

        String equipmentScope = schemaResolver.resolveEquipmentScopeFromDatasetKey(datasetKey);
        if (equipmentScope != null
                && !equipmentScope.isBlank()
                && !"all".equalsIgnoreCase(equipmentScope)
                && !"default".equalsIgnoreCase(equipmentScope)) {
            return canonicalizeEquipmentId(equipmentScope);
        }
        return null;
    }

    private String resolveSensorId(String datasetKey, Object fallbackSensorId) {
        String fromFallback = normalizeOptionalText(fallbackSensorId);
        if (fromFallback != null) {
            return fromFallback;
        }

        String equipmentScope = schemaResolver.resolveEquipmentScopeFromDatasetKey(datasetKey);
        if (equipmentScope != null
                && !equipmentScope.isBlank()
                && !"all".equalsIgnoreCase(equipmentScope)
                && !"default".equalsIgnoreCase(equipmentScope)) {
            return equipmentScope;
        }
        return null;
    }

    private String canonicalizeEquipmentId(Object equipmentId) {
        String normalized = normalizeOptionalText(equipmentId);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private boolean isSupportedAlgoCode(String algoCode) {
        return ALGO_ISOLATION_FOREST.equals(algoCode)
                || ALGO_AUTOENCODER.equals(algoCode)
                || ALGO_RANDOM_FOREST.equals(algoCode);
    }

    private String normalizeRequiredObjectText(Object value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
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

    private Boolean toBoolean(Object value) {
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
            if ("true".equals(normalized) || "y".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "n".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        Double numericValue = toDouble(value);
        if (numericValue == null) {
            return null;
        }
        return numericValue.intValue();
    }

    private Object normalizeMaxSamples(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            Double numericValue = toDouble(trimmed);
            return numericValue != null ? numericValue : trimmed;
        }
        return value.toString();
    }

    private String generateUniqueRunId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String timestamp = RUN_ID_TIME_FORMATTER.format(Instant.now());
            int randomNumber = ThreadLocalRandom.current().nextInt(1000, 9999);
            String candidate = "RUN_" + timestamp + "_" + randomNumber;
            if (!modelTrainRepository.existsRunId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unable to generate unique run_id.");
    }

    private int resolveResultLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RESULT_LIMIT;
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0.");
        }
        return Math.min(limit, MAX_RESULT_LIMIT);
    }

    private int resolveRunListLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RUN_LIST_LIMIT;
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0.");
        }
        return Math.min(limit, MAX_RUN_LIST_LIMIT);
    }

    private int resolveWindowSize(Integer windowSize) {
        if (windowSize == null) {
            throw new IllegalArgumentException("window_size is required.");
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("window_size must be greater than 0.");
        }
        return windowSize;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String normalizeOptionalText(Object value) {
        if (value == null) {
            return null;
        }

        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record FeatureConfigContext(
            String datasetKey,
            Document featurePolicy,
            Document datasetConfig,
            String configSource,
            String configMessage
    ) {
    }

    private record OverviewModelContext(
            String datasetKey,
            String datasetLabel,
            String sourceCollection,
            String activePolicyId,
            String activeAlgoCode,
            String activeAlgoName,
            String summaryType,
            Integer windowSize,
            int selectedColumnCount,
            String regDate,
            String updatedAt,
            AiOverviewLatestRunDto latestRun,
            AiOverviewAnomalySummaryDto summary,
            AiOverviewSupervisedSummaryDto supervisedSummary,
            AiOverviewFeatureSummaryDto featureSummary,
            AiOverviewLabeledDataSummaryDto labeledDataSummary
    ) {
    }

    private record PolicyRunOutcome(
            ModelTrainAutoTriggerResultDto result,
            boolean success
    ) {
    }

    private record RandomForestPayloadRowMeta(
            String labeledDocId,
            String sourceId,
            Map<String, Object> inputFeatures
    ) {
    }

    private record WindowInputRow(
            String lotNo,
            Object windowStart,
            Object windowEnd,
            Map<String, Object> inputFeatures
    ) {
        private Map<String, Object> toAiRowPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("window_start", windowStart);
            payload.put("window_end", windowEnd);
            payload.put("input_features", inputFeatures);
            return payload;
        }
    }

    private record ParameterMappingResult(
            Map<String, Object> modelParams,
            Map<String, Object> originalParams,
            List<String> metaOnlyParamKeys
    ) {
    }

    private record ScoreDistributionStats(
            int count,
            double min,
            double max,
            double mean,
            double stdDev
    ) {
        private static ScoreDistributionStats empty() {
            return new ScoreDistributionStats(0, 0D, 0D, 0D, 0D);
        }

        private boolean hasSpread() {
            return count > 1 && Double.isFinite(min) && Double.isFinite(max) && Double.compare(max, min) > 0;
        }

        private boolean hasStableStdDev() {
            return count > 1 && Double.isFinite(stdDev) && stdDev > SCORE_STD_EPSILON;
        }
    }

    private record HealthCalibrationResult(
            double healthIndex,
            String status,
            double riskScore,
            String mode
    ) {
    }

    private record AlgorithmHealthSnapshot(
            Double rawScore,
            Double normalizedHealth
    ) {
    }

    private record IntegratedHealthContext(
            Double integratedHealth,
            String integratedStatus,
            Double ifNormalizedHealth,
            Double aeNormalizedHealth,
            Double ifScoreRaw,
            Double aeScoreRaw
    ) {
    }

    private static class DatasetAccumulator {
        private final String datasetKey;
        private final LinkedHashSet<String> selectedColumns = new LinkedHashSet<>();
        private Object windowStart;
        private Object windowEnd;
        private String sourceTypeCode;
        private String sourceDtlCode;
        private String sourceFile;
        private String equipmentId;
        private String datasetName;

        private DatasetAccumulator(String datasetKey) {
            this.datasetKey = datasetKey;
        }

        private void addSelectedColumns(List<String> columns) {
            selectedColumns.addAll(columns);
        }

        private void captureWindow(Object candidateWindowStart, Object candidateWindowEnd) {
            if (windowStart == null && candidateWindowStart != null) {
                windowStart = candidateWindowStart;
            }
            if (windowEnd == null && candidateWindowEnd != null) {
                windowEnd = candidateWindowEnd;
            }
        }

        private void captureSource(String candidateTypeCode, String candidateDtlCode, String candidateSourceFile) {
            if (sourceTypeCode == null && candidateTypeCode != null) {
                sourceTypeCode = candidateTypeCode;
            }
            if (sourceDtlCode == null && candidateDtlCode != null) {
                sourceDtlCode = candidateDtlCode;
            }
            if (sourceFile == null && candidateSourceFile != null) {
                sourceFile = candidateSourceFile;
            }
        }

        private void captureEquipmentId(String candidateEquipmentId) {
            if (equipmentId == null && candidateEquipmentId != null) {
                equipmentId = candidateEquipmentId;
            }
        }

        private void captureDatasetName(String candidateDatasetName) {
            if (datasetName == null && candidateDatasetName != null) {
                datasetName = candidateDatasetName;
            }
        }

        private List<String> getSelectedColumnsSorted() {
            return selectedColumns.stream()
                    .distinct()
                    .sorted()
                    .toList();
        }

        private String datasetKey() {
            return datasetKey;
        }

        private String datasetName() {
            return datasetName;
        }

        private Object windowStart() {
            return windowStart;
        }

        private Object windowEnd() {
            return windowEnd;
        }

        private String sourceTypeCode() {
            return sourceTypeCode;
        }

        private String sourceDtlCode() {
            return sourceDtlCode;
        }

        private String sourceFile() {
            return sourceFile;
        }

        private String equipmentId() {
            return equipmentId;
        }
    }
}

