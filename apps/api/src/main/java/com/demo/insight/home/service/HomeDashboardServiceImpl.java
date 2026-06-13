package com.demo.insight.home.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.springframework.stereotype.Service;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.equipment.service.EquipmentMasterService;
import com.demo.insight.home.dto.HomeActiveModelDto;
import com.demo.insight.home.dto.HomeAnomalyTrendPointDto;
import com.demo.insight.home.dto.HomeCorrelationSummaryDto;
import com.demo.insight.home.dto.HomeDashboardKpiDto;
import com.demo.insight.home.dto.HomeDashboardResponseDto;
import com.demo.insight.home.dto.HomeLatestSupervisedDto;
import com.demo.insight.home.dto.HomeRecentRunDto;
import com.demo.insight.home.repository.HomeDashboardRepository;

@Service
public class HomeDashboardServiceImpl implements HomeDashboardService {

    private static final String ALGO_ISOLATION_FOREST = "ISOLATION_FOREST";
    private static final String ALGO_AUTOENCODER = "AUTOENCODER";
    private static final String ALGO_RANDOM_FOREST = "RANDOM_FOREST";

    private static final String ANALYSIS_TYPE_ANOMALY = "ANOMALY_DETECTION";
    private static final String ANALYSIS_TYPE_SUPERVISED = "SUPERVISED_CLASSIFICATION";
    private static final String ANALYSIS_TYPE_UNKNOWN = "UNKNOWN";

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_WARNING = "WARNING";
    private static final String STATUS_NO_DATA = "NO_DATA";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MODEL_TYPE_ANOMALY = "ANOMALY_DETECTION";
    private static final String MODEL_TYPE_SUPERVISED = "SUPERVISED_CLASSIFICATION";

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final int TREND_DAYS = 7;
    private static final int RECENT_RUN_LIMIT = 8;

    private final HomeDashboardRepository homeDashboardRepository;
    private final DynamicSchemaResolver schemaResolver;
    private final EquipmentMasterService equipmentMasterService;

    public HomeDashboardServiceImpl(
            HomeDashboardRepository homeDashboardRepository,
            DynamicSchemaResolver schemaResolver,
            EquipmentMasterService equipmentMasterService
    ) {
        this.homeDashboardRepository = homeDashboardRepository;
        this.schemaResolver = schemaResolver;
        this.equipmentMasterService = equipmentMasterService;
    }

    @Override
    public HomeDashboardResponseDto getDashboard() {
        Instant now = Instant.now();

        ActiveModelBuildResult activeModelBuildResult = buildActiveModels();
        List<HomeActiveModelDto> activeModels = activeModelBuildResult.activeModels();
        int correlationFieldCount = activeModelBuildResult.maxSelectedColumnCount();

        Document latestRun = homeDashboardRepository.findLatestModelRun();
        String latestRunStatus = normalizeRunStatus(latestRun == null ? null : latestRun.get("status"));
        String latestRunAt = normalizeTimestamp(firstNonNull(
                latestRun == null ? null : latestRun.get("updated_at"),
                latestRun == null ? null : latestRun.get("reg_date")
        ));

        ResultSummary latestResultSummary = buildLatestResultSummary(latestRun);
        String latestResultStatus = resolveLatestResultStatus(
                latestResultSummary.totalCount(),
                latestResultSummary.anomalyCount()
        );

        List<Document> activeDatasets = homeDashboardRepository.findActiveDatasetTypes();
        long datasetCount = activeDatasets.size();
        long fieldCount = resolveDatasetFieldCount(activeDatasets);

        String latestUpdatedAt = resolveLatestUpdatedAt(now, latestRun);
        List<HomeAnomalyTrendPointDto> anomalyTrend = buildAnomalyTrend();
        HomeCorrelationSummaryDto correlationSummary = buildCorrelationSummary(correlationFieldCount);
        List<HomeRecentRunDto> recentRuns = buildRecentRuns();
        HomeLatestSupervisedDto latestSupervised = buildLatestSupervisedSummary();

        HomeDashboardKpiDto kpi = new HomeDashboardKpiDto(
                activeModels.size(),
                latestRunStatus,
                latestRunAt,
                latestResultStatus,
                latestResultSummary.anomalyCount(),
                latestResultSummary.totalCount(),
                datasetCount,
                fieldCount,
                latestUpdatedAt
        );

        return new HomeDashboardResponseDto(
                now.toString(),
                kpi,
                List.copyOf(activeModels),
                anomalyTrend,
                correlationSummary,
                recentRuns,
                latestSupervised
        );
    }

    private ActiveModelBuildResult buildActiveModels() {
        List<Document> activeSelections = homeDashboardRepository.findEnabledModelActives();
        List<HomeActiveModelDto> activeModels = new ArrayList<>();
        int maxSelectedColumnCount = 0;

        for (Document activeSelection : activeSelections) {
            String policyId = normalizeText(activeSelection.get("active_policy_id"));
            Document policy = policyId == null ? null : homeDashboardRepository.findPolicyByPolicyId(policyId);

            String datasetKey = schemaResolver.normalizeDatasetKeyString(activeSelection.get("dataset_key"));
            if (datasetKey == null && policy != null) {
                datasetKey = schemaResolver.normalizeDatasetKeyString(policy.get("dataset_key"));
            }
            if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)
                    || equipmentMasterService.isLegacyGlobalDatasetKey(datasetKey)) {
                continue;
            }

            String algoCode = normalizeAlgoCode(firstNonNull(
                    policy == null ? null : policy.get("algo_code"),
                    activeSelection.get("active_algo_code")
            ));
            String algoName = resolveAlgoName(
                    algoCode,
                    firstNonNull(
                            policy == null ? null : policy.get("algo_name"),
                            activeSelection.get("active_algo_name")
                    )
            );

            String modelType = resolveModelType(algoCode);
            int selectedColumnCount = extractSelectedColumnCount(
                    firstNonNull(
                            policy == null ? null : policy.get("selected_columns"),
                            activeSelection.get("selected_columns")
                    )
            );
            if (selectedColumnCount > maxSelectedColumnCount) {
                maxSelectedColumnCount = selectedColumnCount;
            }

            activeModels.add(new HomeActiveModelDto(
                    datasetKey,
                    algoCode,
                    algoName,
                    policyId,
                    modelType,
                    STATUS_ACTIVE
            ));
        }

        return new ActiveModelBuildResult(List.copyOf(activeModels), maxSelectedColumnCount);
    }

    private ResultSummary buildLatestResultSummary(Document latestRun) {
        if (latestRun == null) {
            return new ResultSummary(0L, 0L);
        }

        String runId = normalizeText(latestRun.get("run_id"));
        if (runId == null) {
            return new ResultSummary(0L, 0L);
        }

        String analysisType = resolveAnalysisType(normalizeAlgoCode(latestRun.get("algo_code")));
        if (ANALYSIS_TYPE_ANOMALY.equals(analysisType)) {
            return new ResultSummary(
                    homeDashboardRepository.countAnomalyAlertResultsByRunId(runId),
                    homeDashboardRepository.countAnomalyResultsByRunId(runId)
            );
        }
        if (ANALYSIS_TYPE_SUPERVISED.equals(analysisType)) {
            return new ResultSummary(
                    homeDashboardRepository.countClassificationAnomalyResultsByRunId(runId),
                    homeDashboardRepository.countClassificationResultsByRunId(runId)
            );
        }

        long anomalyCount = homeDashboardRepository.countAnomalyAlertResultsByRunId(runId)
                + homeDashboardRepository.countClassificationAnomalyResultsByRunId(runId);
        long totalCount = homeDashboardRepository.countAnomalyResultsByRunId(runId)
                + homeDashboardRepository.countClassificationResultsByRunId(runId);
        return new ResultSummary(anomalyCount, totalCount);
    }

    private String resolveLatestResultStatus(long totalCount, long anomalyCount) {
        if (totalCount <= 0L) {
            return STATUS_NO_DATA;
        }
        if (anomalyCount > 0L) {
            return STATUS_WARNING;
        }
        return STATUS_NORMAL;
    }

    private long resolveDatasetFieldCount(List<Document> datasetRows) {
        List<String> configuredColumns = schemaResolver.resolveConfiguredColumns("tmst_data_type");
        if (!configuredColumns.isEmpty()) {
            return configuredColumns.size();
        }

        Set<String> fields = new LinkedHashSet<>();
        for (Document datasetRow : datasetRows) {
            fields.addAll(datasetRow.keySet());
        }
        fields.remove("_id");
        return fields.size();
    }

    private String resolveLatestUpdatedAt(Instant now, Document latestRun) {
        Document latestAnomalyResult = homeDashboardRepository.findLatestAnomalyResult();
        Document latestClassificationResult = homeDashboardRepository.findLatestClassificationResult();
        Document latestModelEval = homeDashboardRepository.findLatestModelEval();

        List<Instant> candidates = new ArrayList<>();
        candidates.add(resolveTimestampInstant(firstNonNull(
                latestRun == null ? null : latestRun.get("updated_at"),
                latestRun == null ? null : latestRun.get("reg_date")
        )));
        candidates.add(resolveTimestampInstant(firstNonNull(
                latestAnomalyResult == null ? null : latestAnomalyResult.get("reg_date"),
                latestAnomalyResult == null ? null : latestAnomalyResult.get("window_end")
        )));
        candidates.add(resolveTimestampInstant(
                latestClassificationResult == null ? null : latestClassificationResult.get("reg_date")
        ));
        candidates.add(resolveTimestampInstant(firstNonNull(
                latestModelEval == null ? null : latestModelEval.get("updated_at"),
                latestModelEval == null ? null : latestModelEval.get("reg_date")
        )));

        Instant latest = null;
        for (Instant candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (latest == null || candidate.isAfter(latest)) {
                latest = candidate;
            }
        }

        return (latest == null ? now : latest).toString();
    }

    private List<HomeAnomalyTrendPointDto> buildAnomalyTrend() {
        LocalDate today = LocalDate.now(KST_ZONE);
        LocalDate fromDate = today.minusDays(TREND_DAYS - 1L);
        Instant fromInstant = fromDate.atStartOfDay(KST_ZONE).toInstant();

        Map<String, Long> trendCounts = homeDashboardRepository.aggregateAnomalyTrendCounts(
                Date.from(fromInstant),
                KST_ZONE
        );

        List<HomeAnomalyTrendPointDto> trend = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < TREND_DAYS; dayOffset++) {
            LocalDate currentDay = fromDate.plusDays(dayOffset);
            String dayKey = currentDay.toString();
            trend.add(new HomeAnomalyTrendPointDto(
                    dayKey,
                    trendCounts.getOrDefault(dayKey, 0L)
            ));
        }

        return List.copyOf(trend);
    }

    private HomeCorrelationSummaryDto buildCorrelationSummary(int selectedFieldCount) {
        boolean available = selectedFieldCount >= 2;
        return new HomeCorrelationSummaryDto(
                Math.max(selectedFieldCount, 0),
                available,
                available ? "" : "?곴?愿怨??곗씠?곌? ?놁뒿?덈떎"
        );
    }

    private List<HomeRecentRunDto> buildRecentRuns() {
        List<Document> recentRunDocuments = homeDashboardRepository.findRecentModelRuns(RECENT_RUN_LIMIT);
        List<HomeRecentRunDto> recentRuns = new ArrayList<>();

        for (Document runDocument : recentRunDocuments) {
            String runId = normalizeText(runDocument.get("run_id"));
            if (runId == null) {
                continue;
            }

            String algoCode = normalizeAlgoCode(runDocument.get("algo_code"));
            String analysisType = resolveAnalysisType(algoCode);
            String runAt = normalizeTimestamp(firstNonNull(
                    runDocument.get("updated_at"),
                    runDocument.get("reg_date")
            ));
            String status = normalizeRunStatus(runDocument.get("status"));
            String datasetKey = schemaResolver.normalizeDatasetKeyString(runDocument.get("dataset_key"));
            String algoName = resolveAlgoName(algoCode, runDocument.get("algo_name"));

            long resultCount = 0L;
            Long anomalyCount = null;
            Double accuracy = null;
            Double f1Score = null;

            if (ANALYSIS_TYPE_ANOMALY.equals(analysisType)) {
                resultCount = homeDashboardRepository.countAnomalyResultsByRunId(runId);
                anomalyCount = homeDashboardRepository.countAnomalyAlertResultsByRunId(runId);
            } else if (ANALYSIS_TYPE_SUPERVISED.equals(analysisType)) {
                resultCount = homeDashboardRepository.countClassificationResultsByRunId(runId);
                Document latestModelEval = homeDashboardRepository.findLatestModelEvalByRunId(runId);
                accuracy = toFiniteDouble(latestModelEval == null ? null : latestModelEval.get("accuracy"));
                f1Score = toFiniteDouble(latestModelEval == null ? null : latestModelEval.get("f1_score"));
            }

            recentRuns.add(new HomeRecentRunDto(
                    runId,
                    runAt,
                    algoCode,
                    algoName,
                    datasetKey,
                    analysisType,
                    status,
                    resultCount,
                    anomalyCount,
                    accuracy,
                    f1Score
            ));
        }

        return List.copyOf(recentRuns);
    }

    private HomeLatestSupervisedDto buildLatestSupervisedSummary() {
        Document latestSupervisedRun = homeDashboardRepository.findLatestModelRunByAlgoCode(ALGO_RANDOM_FOREST);
        if (latestSupervisedRun == null) {
            return new HomeLatestSupervisedDto(
                    false,
                    null,
                    null,
                    STATUS_NO_DATA,
                    0L,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    "吏?꾪븰??寃곌낵 ?곗씠?곌? ?놁뒿?덈떎"
            );
        }

        String runId = normalizeText(latestSupervisedRun.get("run_id"));
        if (runId == null) {
            return new HomeLatestSupervisedDto(
                    false,
                    null,
                    null,
                    STATUS_NO_DATA,
                    0L,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    "吏?꾪븰??寃곌낵 ?곗씠?곌? ?놁뒿?덈떎"
            );
        }

        long resultCount = homeDashboardRepository.countClassificationResultsByRunId(runId);
        long anomalyCount = homeDashboardRepository.countClassificationAnomalyResultsByRunId(runId);
        Document latestModelEval = homeDashboardRepository.findLatestModelEvalByRunId(runId);

        return new HomeLatestSupervisedDto(
                resultCount > 0L || latestModelEval != null,
                runId,
                normalizeTimestamp(firstNonNull(
                        latestSupervisedRun.get("updated_at"),
                        latestSupervisedRun.get("reg_date")
                )),
                normalizeRunStatus(latestSupervisedRun.get("status")),
                resultCount,
                anomalyCount,
                toFiniteDouble(latestModelEval == null ? null : latestModelEval.get("accuracy")),
                toFiniteDouble(latestModelEval == null ? null : latestModelEval.get("precision")),
                toFiniteDouble(latestModelEval == null ? null : latestModelEval.get("recall")),
                toFiniteDouble(latestModelEval == null ? null : latestModelEval.get("f1_score")),
                resultCount > 0L || latestModelEval != null
                        ? ""
                        : "吏?꾪븰??寃곌낵 ?곗씠?곌? ?놁뒿?덈떎"
        );
    }

    private String normalizeRunStatus(Object rawStatus) {
        String normalized = normalizeUpperText(rawStatus);
        if (normalized == null) {
            return STATUS_NO_DATA;
        }

        return switch (normalized) {
            case STATUS_SUCCESS -> STATUS_SUCCESS;
            case STATUS_FAIL, STATUS_FAILED -> STATUS_FAIL;
            case STATUS_RUNNING -> STATUS_RUNNING;
            case STATUS_SKIPPED -> STATUS_SKIPPED;
            default -> normalized;
        };
    }

    private String normalizeAlgoCode(Object rawAlgoCode) {
        String normalized = normalizeUpperText(rawAlgoCode);
        if (normalized == null) {
            return null;
        }
        if (normalized.contains("ISOLATION") && normalized.contains("FOREST")) {
            return ALGO_ISOLATION_FOREST;
        }
        if (normalized.contains("AUTOENCODER")) {
            return ALGO_AUTOENCODER;
        }
        if (normalized.contains("RANDOM") && normalized.contains("FOREST")) {
            return ALGO_RANDOM_FOREST;
        }
        return normalized;
    }

    private String resolveAlgoName(String algoCode, Object rawAlgoName) {
        String algoName = normalizeText(rawAlgoName);
        if (algoName != null) {
            return algoName;
        }
        if (ALGO_ISOLATION_FOREST.equals(algoCode)) {
            return "Isolation Forest";
        }
        if (ALGO_AUTOENCODER.equals(algoCode)) {
            return "Autoencoder";
        }
        if (ALGO_RANDOM_FOREST.equals(algoCode)) {
            return "Random Forest";
        }
        return algoCode == null ? null : algoCode;
    }

    private String resolveModelType(String algoCode) {
        if (ALGO_RANDOM_FOREST.equals(algoCode)) {
            return MODEL_TYPE_SUPERVISED;
        }
        if (ALGO_ISOLATION_FOREST.equals(algoCode) || ALGO_AUTOENCODER.equals(algoCode)) {
            return MODEL_TYPE_ANOMALY;
        }
        return MODEL_TYPE_ANOMALY;
    }

    private String resolveAnalysisType(String algoCode) {
        if (ALGO_RANDOM_FOREST.equals(algoCode)) {
            return ANALYSIS_TYPE_SUPERVISED;
        }
        if (ALGO_ISOLATION_FOREST.equals(algoCode) || ALGO_AUTOENCODER.equals(algoCode)) {
            return ANALYSIS_TYPE_ANOMALY;
        }
        return ANALYSIS_TYPE_UNKNOWN;
    }

    private int extractSelectedColumnCount(Object selectedColumnsObject) {
        if (selectedColumnsObject instanceof List<?> selectedColumns) {
            int count = 0;
            for (Object selectedColumn : selectedColumns) {
                if (normalizeText(selectedColumn) != null) {
                    count++;
                }
            }
            return count;
        }
        return 0;
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeUpperText(Object value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeTimestamp(Object timestampValue) {
        Instant instant = resolveTimestampInstant(timestampValue);
        return instant == null ? null : instant.toString();
    }

    private Instant resolveTimestampInstant(Object timestampValue) {
        if (timestampValue == null) {
            return null;
        }
        if (timestampValue instanceof Instant instantValue) {
            return instantValue;
        }
        if (timestampValue instanceof Date dateValue) {
            return dateValue.toInstant();
        }
        if (timestampValue instanceof Number numberValue) {
            long millis = numberValue.longValue();
            if (millis <= 0L) {
                return null;
            }
            return Instant.ofEpochMilli(millis);
        }

        String normalized = normalizeText(timestampValue);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double toFiniteDouble(Object value) {
        if (value instanceof Number numberValue) {
            double number = numberValue.doubleValue();
            return Double.isFinite(number) ? number : null;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                double parsed = Double.parseDouble(normalized);
                return Double.isFinite(parsed) ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object firstNonNull(Object left, Object right) {
        return left != null ? left : right;
    }

    private record ActiveModelBuildResult(
            List<HomeActiveModelDto> activeModels,
            int maxSelectedColumnCount
    ) {
    }

    private record ResultSummary(
            long anomalyCount,
            long totalCount
    ) {
    }
}
