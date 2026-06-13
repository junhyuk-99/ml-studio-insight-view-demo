package com.demo.insight.preprocess.service;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.equipment.service.EquipmentMasterService;
import com.demo.insight.preprocess.domain.DataTypeDatasetDocument;
import com.demo.insight.preprocess.domain.DataTypeDetailDocument;
import com.demo.insight.preprocess.domain.DataTypeMasterDocument;
import com.demo.insight.preprocess.domain.PrepOptionDetailDocument;
import com.demo.insight.preprocess.domain.PrepTypeMapDocument;
import com.demo.insight.preprocess.domain.PrepTypeMasterDocument;
import com.demo.insight.preprocess.dto.DataSourceDatasetDto;
import com.demo.insight.preprocess.dto.DataSourceDetailDto;
import com.demo.insight.preprocess.dto.DataSourceListResponseDto;
import com.demo.insight.preprocess.dto.DataSourceTypeDto;
import com.demo.insight.preprocess.dto.FeatureGenerationRequestDto;
import com.demo.insight.preprocess.dto.FeatureGenerationResponseDto;
import com.demo.insight.preprocess.dto.FeaturePreviewResponseDto;
import com.demo.insight.preprocess.dto.PrepOptionDto;
import com.demo.insight.preprocess.dto.PrepTypeDto;
import com.demo.insight.preprocess.dto.PreprocessOptionResponseDto;
import com.demo.insight.preprocess.dto.RawDataPreviewResponseDto;
import com.demo.insight.preprocess.repository.DataTypeDatasetRepository;
import com.demo.insight.preprocess.repository.DataTypeDetailRepository;
import com.demo.insight.preprocess.repository.DataTypeMasterRepository;
import com.demo.insight.preprocess.repository.FeatureAutoJobRepository;
import com.demo.insight.preprocess.repository.FeatureRepository;
import com.demo.insight.preprocess.repository.PrepOptionDetailRepository;
import com.demo.insight.preprocess.repository.PrepTypeMapRepository;
import com.demo.insight.preprocess.repository.PrepTypeMasterRepository;
import com.demo.insight.preprocess.repository.RawPreviewRepository;
import com.demo.insight.preprocess.repository.RawPreviewResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PreprocessServiceImpl implements PreprocessService {

    private static final Logger log = LoggerFactory.getLogger(PreprocessServiceImpl.class);

    private static final String ACTIVE_YN = "Y";
    private static final int DEFAULT_RAW_PREVIEW_LIMIT = 200;
    private static final int MAX_RAW_PREVIEW_LIMIT = 200;
    private static final int DEFAULT_FEATURE_PREVIEW_LIMIT = 30;
    private static final int MAX_FEATURE_PREVIEW_LIMIT = 50;
    private static final boolean DEFAULT_FEATURE_PREVIEW_COMPACT = true;
    private static final String SOURCE_COLLECTION = "THISHMIDATA";
    private static final String LEGACY_GLOBAL_DATASET_KEY = "demo_hmi_all_default_v1";
    private static final String DEFAULT_DATASET_NAME = "Demo HMI Dataset";
    private static final String TIMESTAMP_FIELD = "PRDTIME";
    private static final String EQUIPMENT_FIELD = "MCCODE";
    private static final String DEFAULT_DATASET_PURPOSE = "FEATURE_SOURCE";
    private static final boolean DEFAULT_FEATURE_ENABLED = true;
    private static final String DEFAULT_EQUIPMENT_SCOPE = "all";
    private static final String DEFAULT_POLICY_NAME = "default";
    private static final int DEFAULT_POLICY_VERSION = 1;
    private static final Pattern COLLECTION_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final double MIN_NON_NULL_RATIO_FOR_FEATURE = 0.20D;

    private static final List<String> FEATURE_STAT_KEYS = List.of("MEAN", "STD", "MIN", "MAX");

    private final DataTypeMasterRepository dataTypeMasterRepository;
    private final DataTypeDetailRepository dataTypeDetailRepository;
    private final DataTypeDatasetRepository dataTypeDatasetRepository;
    private final RawPreviewRepository rawPreviewRepository;
    private final FeatureRepository featureRepository;
    private final FeatureAutoJobRepository featureAutoJobRepository;
    private final PrepTypeMasterRepository prepTypeMasterRepository;
    private final PrepTypeMapRepository prepTypeMapRepository;
    private final PrepOptionDetailRepository prepOptionDetailRepository;
    private final DynamicSchemaResolver schemaResolver;
    private final EquipmentMasterService equipmentMasterService;
    private final ObjectMapper objectMapper;

    public PreprocessServiceImpl(
            DataTypeMasterRepository dataTypeMasterRepository,
            DataTypeDetailRepository dataTypeDetailRepository,
            DataTypeDatasetRepository dataTypeDatasetRepository,
            RawPreviewRepository rawPreviewRepository,
            FeatureRepository featureRepository,
            FeatureAutoJobRepository featureAutoJobRepository,
            PrepTypeMasterRepository prepTypeMasterRepository,
            PrepTypeMapRepository prepTypeMapRepository,
            PrepOptionDetailRepository prepOptionDetailRepository,
            DynamicSchemaResolver schemaResolver,
            EquipmentMasterService equipmentMasterService,
            ObjectMapper objectMapper
    ) {
        this.dataTypeMasterRepository = dataTypeMasterRepository;
        this.dataTypeDetailRepository = dataTypeDetailRepository;
        this.dataTypeDatasetRepository = dataTypeDatasetRepository;
        this.rawPreviewRepository = rawPreviewRepository;
        this.featureRepository = featureRepository;
        this.featureAutoJobRepository = featureAutoJobRepository;
        this.prepTypeMasterRepository = prepTypeMasterRepository;
        this.prepTypeMapRepository = prepTypeMapRepository;
        this.prepOptionDetailRepository = prepOptionDetailRepository;
        this.schemaResolver = schemaResolver;
        this.equipmentMasterService = equipmentMasterService;
        this.objectMapper = objectMapper;
    }

    @Override
    public DataSourceListResponseDto getDataSources() {
        List<DataTypeMasterDocument> masterDocuments = dataTypeMasterRepository.findByUseFlagOrderBySortNoAsc(ACTIVE_YN);
        List<DataTypeDetailDocument> detailDocuments = dataTypeDetailRepository.findByUseFlagOrderByTypeCodeAscSortNoAsc(ACTIVE_YN);
        List<DataTypeDatasetDocument> datasetDocuments = dataTypeDatasetRepository.findByUseFlagOrderByTypeCodeAscDtlCodeAscSortNoAsc(ACTIVE_YN);

        Map<String, DataSourceTypeDto> dataTypeMap = new LinkedHashMap<>();
        Map<String, DataSourceDetailDto> detailMap = new LinkedHashMap<>();
        for (DataTypeMasterDocument masterDocument : masterDocuments) {
            dataTypeMap.put(masterDocument.getTypeCode(), new DataSourceTypeDto(
                    masterDocument.getTypeCode(),
                    masterDocument.getTypeName(),
                    masterDocument.getSortNo()
            ));
        }

        for (DataTypeDetailDocument detailDocument : detailDocuments) {
            DataSourceTypeDto parentType = dataTypeMap.get(detailDocument.getTypeCode());
            if (parentType == null) {
                continue;
            }
            DataSourceDetailDto detailDto = new DataSourceDetailDto(
                    detailDocument.getDtlCode(),
                    detailDocument.getDtlName(),
                    detailDocument.getSortNo()
            );
            parentType.getDetails().add(detailDto);
            detailMap.put(buildDataTypeKey(detailDocument.getTypeCode(), detailDocument.getDtlCode()), detailDto);
        }

        for (DataTypeDatasetDocument datasetDocument : datasetDocuments) {
            String typeCode = normalizeOptionalCode(datasetDocument.getTypeCode());
            String dtlCode = normalizeOptionalCode(datasetDocument.getDtlCode());
            String datasetKey = normalizeOptionalCode(datasetDocument.getDatasetKey());
            if (typeCode == null || dtlCode == null || datasetKey == null) {
                continue;
            }
            if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)
                    || equipmentMasterService.isLegacyGlobalDatasetKey(datasetKey)) {
                continue;
            }

            DataSourceDetailDto detailDto = detailMap.get(buildDataTypeKey(typeCode, dtlCode));
            if (detailDto == null) {
                continue;
            }

            detailDto.getDatasets().add(new DataSourceDatasetDto(
                    datasetKey,
                    normalizeOptionalCode(datasetDocument.getDatasetName()),
                    normalizeOptionalCode(datasetDocument.getDisplayName()),
                    resolveCollectionName(datasetDocument.getSourceCollection(), SOURCE_COLLECTION),
                    normalizeOptionalCode(datasetDocument.getTargetFeatureCollection()),
                    resolveDatasetPurpose(datasetDocument.getDatasetPurpose()),
                    resolveFeatureEnabled(datasetDocument.getFeatureEnabled()),
                    normalizeOptionalCode(datasetDocument.getLabelField()),
                    datasetDocument.getSortNo(),
                    normalizeOptionalCode(datasetDocument.getEquipmentGroup()),
                    normalizeOptionalCode(datasetDocument.getEquipmentGroupName())
            ));
        }

        return new DataSourceListResponseDto(dataTypeMap.values().stream().toList());
    }

    @Override
    public RawDataPreviewResponseDto getRawDataPreview(
            String datasetKey,
            String typeCode,
            String dtlCode,
            String from,
            String to,
            String equipmentId,
            Integer limit
    ) {
        String normalizedTypeCode = normalizeOptionalCode(typeCode);
        String normalizedDtlCode = normalizeOptionalCode(dtlCode);
        DatasetSelection datasetSelection = resolveDatasetSelection(datasetKey);
        int resolvedLimit = resolveRawPreviewLimit(limit);

        DataTypeDatasetDocument activeDataset =
                dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(
                        datasetSelection.datasetKey(),
                        ACTIVE_YN
                );

        boolean featureEnabled = activeDataset == null
                || resolveFeatureEnabled(activeDataset.getFeatureEnabled());

        Map<String, Object> rawDatasetFilter = Map.of();
        Document appliedMatchFilter = new Document();

        if (featureEnabled) {
            Document policy = resolveFeaturePolicy(datasetSelection.datasetKey());
            RawDatasetFilterSpec rawDatasetFilterSpec = resolveRawDatasetFilterSpec(
                    policy,
                    datasetSelection.datasetKey()
            );
            rawDatasetFilter = rawDatasetFilterSpec.rawDatasetFilter();
            appliedMatchFilter = rawDatasetFilterSpec.appliedMatchFilter();
        }

        RawPreviewResult previewResult = rawPreviewRepository.findPreviewRows(
                datasetSelection.sourceCollection(),
                rawDatasetFilter,
                normalizedTypeCode,
                normalizedDtlCode,
                from,
                to,
                equipmentId,
                resolvedLimit
        );

        log.info(
                "Raw preview loaded. datasetKey={}, sourceCollection={}, featureEnabled={}, rawDatasetFilter={}, appliedMatchFilter={}, rowCount={}",
                datasetSelection.datasetKey(),
                datasetSelection.sourceCollection(),
                featureEnabled,
                rawDatasetFilter,
                appliedMatchFilter,
                previewResult.rawRows().size()
        );

        return new RawDataPreviewResponseDto(
                datasetSelection.sourceCollection(),
                datasetSelection.datasetKey(),
                datasetSelection.datasetName(),
                datasetSelection.datasetDisplayName(),
                previewResult.availableColumns(),
                previewResult.metadataColumns(),
                previewResult.numericColumns(),
                previewResult.columnLabels(),
                previewResult.datasetKeyColumns(),
                previewResult.datasetKeys(),
                previewResult.rawRows()
        );
    }

    @Override
    public PreprocessOptionResponseDto getPreprocessOptions() {
        List<PrepTypeMasterDocument> prepTypeDocuments = prepTypeMasterRepository.findByUseYnOrderBySortOrdAsc(ACTIVE_YN);
        List<PrepTypeMapDocument> mappingDocuments = prepTypeMapRepository.findByUseYnOrderByPrepTypeCdAscSortOrdAsc(ACTIVE_YN);

        Set<String> activePrepCodes = mappingDocuments.stream()
                .map(PrepTypeMapDocument::getPrepCd)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<PrepOptionDetailDocument> optionDocuments = activePrepCodes.isEmpty()
                ? List.of()
                : prepOptionDetailRepository.findByUseYnAndPrepCdIn(ACTIVE_YN, new ArrayList<>(activePrepCodes));

        Map<String, PrepOptionDetailDocument> optionMap = optionDocuments.stream()
                .collect(Collectors.toMap(
                        PrepOptionDetailDocument::getPrepCd,
                        Function.identity(),
                        (left, right) -> left
                ));

        List<PrepTypeDto> prepTypes = prepTypeDocuments.stream()
                .map(type -> new PrepTypeDto(
                        type.getPrepTypeCd(),
                        type.getPrepTypeNm(),
                        type.getDesc(),
                        type.getSortOrd()
                ))
                .toList();

        Map<String, List<PrepOptionDto>> optionsByType = new LinkedHashMap<>();
        for (PrepTypeDto prepType : prepTypes) {
            optionsByType.put(prepType.prepTypeCd(), new ArrayList<>());
        }

        for (PrepTypeMapDocument mappingDocument : mappingDocuments) {
            List<PrepOptionDto> options = optionsByType.get(mappingDocument.getPrepTypeCd());
            if (options == null) {
                continue;
            }

            PrepOptionDetailDocument optionDetail = optionMap.get(mappingDocument.getPrepCd());
            if (optionDetail == null) {
                continue;
            }

            options.add(new PrepOptionDto(
                    optionDetail.getPrepCd(),
                    optionDetail.getPrepNm(),
                    mappingDocument.getSortOrd()
            ));
        }

        return new PreprocessOptionResponseDto(prepTypes, optionsByType);
    }

    @Override
    public FeatureGenerationResponseDto generateFeatures(FeatureGenerationRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String datasetKey = resolveDatasetKey(request.datasetKey(), request.equipmentId(), request.sensorId());
        if (datasetKey == null) {
            throw new IllegalArgumentException("dataset_key is required.");
        }
        if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)) {
            throw new IllegalArgumentException("Deprecated dataset_key is not allowed in demo runtime: " + datasetKey);
        }

        Document policy = resolveFeaturePolicy(datasetKey);
        String sourceCollection = resolveSourceCollectionForDatasetKey(datasetKey);
        String datasetName = asString(policy.get("dataset_name"), sourceCollection);
        List<String> selectedColumns = resolveSelectedColumns(policy, request.selectedColumns());
        int windowSize = resolveWindowSizeFromPolicy(policy, request.windowSize());
        String targetCollection = resolveTargetCollection(asString(policy.get("target_collection"), null));
        normalizeMode(request.mode());
        RawDatasetFilterSpec rawDatasetFilterSpec = resolveRawDatasetFilterSpec(policy, datasetKey);

        List<Document> rawRows = rawPreviewRepository.findRowsByDatasetKey(
                sourceCollection,
                rawDatasetFilterSpec.rawDatasetFilter()
        );
        log.info(
                "Manual feature raw rows resolved. datasetKey={}, sourceCollection={}, rawRows={}, appliedMatchFilter={}, rawDatasetFilter={}",
                datasetKey,
                sourceCollection,
                rawRows.size(),
                rawDatasetFilterSpec.appliedMatchFilter(),
                rawDatasetFilterSpec.rawDatasetFilter()
        );
        if (rawRows.isEmpty()) {
            log.info("Feature generation skipped because no raw rows matched. datasetKey={}", datasetKey);
            return new FeatureGenerationResponseDto(0, 0, 0);
        }

        List<String> numericColumns = resolveNumericColumns(rawRows, selectedColumns);
        if (numericColumns.isEmpty()) {
            throw new IllegalArgumentException("selected_columns must contain at least one numeric column.");
        }

        List<Document> featureRows = buildFeatureRows(
                rawRows,
                datasetKey,
                datasetName,
                numericColumns,
                windowSize,
                targetCollection,
                sourceCollection,
                rawDatasetFilterSpec.matchFilter(),
                rawDatasetFilterSpec.appliedMatchFilter()
        );
        int createdCount = featureRepository.upsertFeatureRows(featureRows, targetCollection);

        log.info(
                "Feature generation completed. datasetKey={}, rawRows={}, windows={}, created={}, skipped={}, appliedMatchFilter={}",
                datasetKey,
                rawRows.size(),
                featureRows.size(),
                createdCount,
                Math.max(featureRows.size() - createdCount, 0),
                rawDatasetFilterSpec.appliedMatchFilter()
        );

        return new FeatureGenerationResponseDto(
                featureRows.size(),
                createdCount,
                Math.max(featureRows.size() - createdCount, 0)
        );
    }

    @Override
    public FeaturePreviewResponseDto getFeaturePreview(
            String datasetKeyJson,
            String equipmentId,
            String sensorId,
            Integer limit,
            Boolean compact
    ) {
        String datasetKey = resolveDatasetKey(datasetKeyJson, equipmentId, sensorId);
        int resolvedLimit = resolveFeaturePreviewLimit(limit);
        boolean compactPreview = resolveFeaturePreviewCompact(compact);

        List<Map<String, Object>> featureRows = featureRepository.findFeatureRowsByDatasetKey(
                datasetKey,
                resolvedLimit,
                compactPreview
        );

        log.info(
                "Feature preview loaded. datasetKey={}, limit={}, compact={}, rowCount={}",
                datasetKey,
                resolvedLimit,
                compactPreview,
                featureRows.size()
        );

        LinkedHashSet<String> availableColumns = new LinkedHashSet<>(schemaResolver.resolveConfiguredColumns("thisfeature"));
        for (Map<String, Object> featureRow : featureRows) {
            availableColumns.addAll(featureRow.keySet());
        }
        availableColumns.remove("_id");

        return new FeaturePreviewResponseDto(
                datasetKey,
                List.copyOf(availableColumns),
                featureRows
        );
    }

    private List<Document> buildFeatureRows(
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

                Instant normalizedWindowStart = normalizeTimestamp(windowStart);
                Instant normalizedWindowEnd = normalizeTimestamp(windowEnd);

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

                String resolvedEquipmentId = normalizeOptionalCode(asString(
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
                        Double numericValue = toDouble(row.get(column));
                        if (numericValue != null) {
                            numericValues.add(numericValue);
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
            }
        }

        return featureRows;
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

    private List<String> resolveNumericColumns(List<Document> rawRows, List<String> selectedColumns) {
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
                    "Selected column excluded due to low non-null ratio. column={}, nonNullCount={}, totalRows={}, nonNullRatio={}",
                    column,
                    nonNullNumericCount,
                    totalRows,
                    nonNullRatio
            );
        }
        return numericColumns;
    }

    private int resolveRawPreviewLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RAW_PREVIEW_LIMIT;
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0.");
        }
        return Math.min(limit, MAX_RAW_PREVIEW_LIMIT);
    }

    private int resolveFeaturePreviewLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_FEATURE_PREVIEW_LIMIT;
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0.");
        }
        return Math.min(limit, MAX_FEATURE_PREVIEW_LIMIT);
    }

    private boolean resolveFeaturePreviewCompact(Boolean compact) {
        if (compact == null) {
            return DEFAULT_FEATURE_PREVIEW_COMPACT;
        }
        return compact;
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

    private String normalizeMode(String mode) {
        String normalized = normalizeRequiredValue(mode, "mode").toLowerCase(Locale.ROOT);
        if (!"auto".equals(normalized) && !"manual".equals(normalized)) {
            throw new IllegalArgumentException("mode must be one of: auto, manual.");
        }
        return normalized;
    }

    private String resolveDatasetKey(String datasetKeyValue, String equipmentId, String sensorId) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKeyValue);
        if (normalizedDatasetKey != null) {
            DataTypeDatasetDocument datasetDocument =
                    dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(normalizedDatasetKey, ACTIVE_YN);
            if (datasetDocument != null) {
                return normalizedDatasetKey;
            }
            if (schemaResolver.isDatasetKeyPolicyFormat(normalizedDatasetKey)) {
                return normalizedDatasetKey;
            }
            if (SOURCE_COLLECTION.equalsIgnoreCase(normalizedDatasetKey)) {
                return resolveDefaultOperationalDatasetKey();
            }
            return normalizedDatasetKey;
        }

        if (datasetKeyValue != null && !datasetKeyValue.isBlank()) {
            try {
                Map<String, Object> rawDatasetKey = objectMapper.readValue(datasetKeyValue, new TypeReference<>() {
                });
                String nestedDatasetKey = schemaResolver.normalizeDatasetKeyString(rawDatasetKey.get("dataset_key"));
                if (nestedDatasetKey != null) {
                    DataTypeDatasetDocument nestedDatasetDocument =
                            dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(nestedDatasetKey, ACTIVE_YN);
                    if (nestedDatasetDocument != null || schemaResolver.isDatasetKeyPolicyFormat(nestedDatasetKey)) {
                        return nestedDatasetKey;
                    }
                    if (SOURCE_COLLECTION.equalsIgnoreCase(nestedDatasetKey)) {
                        return resolveDefaultOperationalDatasetKey();
                    }
                    return nestedDatasetKey;
                }
                throw new IllegalArgumentException("dataset_key must be a non-empty string.");
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("dataset_key must be a valid string or JSON object containing dataset_key.", exception);
            }
        }

        String fromEquipment = equipmentMasterService.resolveDatasetKeyByEquipment(equipmentId);
        if (fromEquipment != null) {
            return fromEquipment;
        }

        if (normalizeOptionalCode(sensorId) != null) {
            return resolveDefaultOperationalDatasetKey();
        }
        return resolveDefaultOperationalDatasetKey();
    }

    private Document resolveFeaturePolicy(String datasetKey) {
        Document policy = featureAutoJobRepository.findByJobId(datasetKey);
        Document datasetConfig = featureAutoJobRepository.findDatasetConfigByDatasetKey(datasetKey);
        if (policy == null && datasetConfig == null) {
            log.warn("Feature policy not found for dataset_key={}. Falling back to request values.", datasetKey);
            return new Document();
        }

        Document resolvedPolicy = policy == null ? new Document() : cloneDocument(policy);
        mergePolicyFieldIfMissing(resolvedPolicy, "selected_columns", datasetConfig == null ? null : datasetConfig.get("selected_columns"));
        mergePolicyFieldIfMissing(resolvedPolicy, "window_size", datasetConfig == null ? null : datasetConfig.get("window_size"));
        mergePolicyFieldIfMissing(resolvedPolicy, "target_collection", datasetConfig == null ? null : datasetConfig.get("target_collection"));
        mergePolicyFieldIfMissing(resolvedPolicy, "source_collection", datasetConfig == null ? null : datasetConfig.get("source_collection"));
        mergePolicyFieldIfMissing(
                resolvedPolicy,
                "equipment_id_field",
                datasetConfig == null
                        ? null
                        : firstNonBlank(datasetConfig.get("equipment_id_field"), datasetConfig.get("equipment_id"))
        );
        mergePolicyFieldIfMissing(resolvedPolicy, "match_filter", datasetConfig == null ? null : datasetConfig.get("match_filter"));
        return resolvedPolicy;
    }

    private List<String> resolveSelectedColumns(Document policy, List<String> selectedColumnsFromRequest) {
        List<String> selectedColumnsFromPolicy = asStringList(policy.get("selected_columns"));
        if (!selectedColumnsFromPolicy.isEmpty()) {
            return normalizeSelectedColumns(selectedColumnsFromPolicy);
        }
        return normalizeSelectedColumns(selectedColumnsFromRequest);
    }

    private int resolveWindowSizeFromPolicy(Document policy, Integer requestedWindowSize) {
        Integer configuredWindowSize = asInteger(policy.get("window_size"));
        if (configuredWindowSize != null) {
            return resolveWindowSize(configuredWindowSize);
        }
        return resolveWindowSize(requestedWindowSize);
    }

    private Map<String, Object> resolveRawDatasetFilter(Document policy, String datasetKey) {
        return resolveRawDatasetFilterSpec(policy, datasetKey).rawDatasetFilter();
    }

    private RawDatasetFilterSpec resolveRawDatasetFilterSpec(Document policy, String datasetKey) {
        Document matchFilter = resolveMatchFilter(policy, datasetKey);
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

            if (datasetEquipmentId == null || !datasetEquipmentId.equalsIgnoreCase(matchFilterEquipmentId)) {
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
            return normalizeOptionalCode(textMccode);
        }
        if (rawMccode instanceof Document mccodeDocument) {
            Object eqValue = mccodeDocument.get("$eq");
            if (eqValue instanceof String textEqValue) {
                return normalizeOptionalCode(textEqValue);
            }
        }
        return null;
    }

    private String resolveTargetCollection(String targetCollection) {
        if (targetCollection == null || targetCollection.isBlank()) {
            return "thisfeature";
        }
        return targetCollection.trim();
    }

    private String resolveEquipmentScope(String equipmentId, String sensorId) {
        String normalizedEquipmentId = normalizeOptionalCode(equipmentId);
        if (normalizedEquipmentId != null) {
            return normalizedEquipmentId;
        }
        String normalizedSensorId = normalizeOptionalCode(sensorId);
        if (normalizedSensorId != null) {
            return normalizedSensorId;
        }
        return DEFAULT_EQUIPMENT_SCOPE;
    }

    private DatasetSelection resolveDatasetSelection(String requestedDatasetKey) {
        String normalizedDatasetKey = normalizeOptionalCode(requestedDatasetKey);
        if (normalizedDatasetKey != null
                && (equipmentMasterService.isDeprecatedRuntimeDatasetKey(normalizedDatasetKey)
                || equipmentMasterService.isLegacyGlobalDatasetKey(normalizedDatasetKey))) {
            normalizedDatasetKey = null;
        }
        if (normalizedDatasetKey == null) {
            String defaultDatasetKey = resolveDefaultOperationalDatasetKey();
            return new DatasetSelection(
                    defaultDatasetKey,
                    DEFAULT_DATASET_NAME,
                    DEFAULT_DATASET_NAME,
                    SOURCE_COLLECTION
            );
        }

        DataTypeDatasetDocument datasetDocument = dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(normalizedDatasetKey, ACTIVE_YN);
        if (datasetDocument == null) {
            throw new IllegalArgumentException("Active dataset not found for datasetKey: " + normalizedDatasetKey);
        }

        String sourceCollection = resolveCollectionName(datasetDocument.getSourceCollection(), SOURCE_COLLECTION);
        String datasetName = normalizeOptionalCode(datasetDocument.getDatasetName());
        String datasetDisplayName = normalizeOptionalCode(datasetDocument.getDisplayName());
        return new DatasetSelection(
                normalizeOptionalCode(datasetDocument.getDatasetKey()),
                datasetName,
                datasetDisplayName,
                sourceCollection
        );
    }

    private String resolveSourceCollectionForDatasetKey(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return SOURCE_COLLECTION;
        }

        DataTypeDatasetDocument datasetDocument = dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(normalizedDatasetKey, ACTIVE_YN);
        if (datasetDocument != null) {
            return resolveCollectionName(datasetDocument.getSourceCollection(), SOURCE_COLLECTION);
        }

        String[] tokens = normalizedDatasetKey.split("_");
        if (tokens.length == 0) {
            return SOURCE_COLLECTION;
        }
        return SOURCE_COLLECTION;
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        String normalized = value.toString().trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private void mergePolicyFieldIfMissing(Document targetPolicy, String fieldName, Object value) {
        if (targetPolicy == null || fieldName == null || fieldName.isBlank()) {
            return;
        }

        if (targetPolicy.containsKey(fieldName) && targetPolicy.get(fieldName) != null) {
            return;
        }

        Object cloned = cloneValue(value);
        if (cloned == null) {
            return;
        }
        targetPolicy.put(fieldName, cloned);
    }

    private Document resolveMatchFilter(Document policy, String datasetKey) {
        Document matchFilter = extractMatchFilter(policy);
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
            List<Object> cloned = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                cloned.add(cloneValue(item));
            }
            return cloned;
        }
        return value;
    }

    private String normalizeRequiredValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String normalizeOptionalCode(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildDataTypeKey(String typeCode, String dtlCode) {
        return (typeCode == null ? "" : typeCode.trim())
                + "::"
                + (dtlCode == null ? "" : dtlCode.trim());
    }

    private String resolveCollectionName(String requestedCollectionName, String fallbackCollectionName) {
        String fallback = normalizeOptionalCode(fallbackCollectionName);
        String safeFallback = fallback == null ? SOURCE_COLLECTION : fallback;
        String normalized = normalizeOptionalCode(requestedCollectionName);
        if (normalized == null) {
            return safeFallback;
        }
        if (!COLLECTION_NAME_PATTERN.matcher(normalized).matches()) {
            log.warn("Invalid source collection name detected. sourceCollection={}, fallback={}", normalized, safeFallback);
            return safeFallback;
        }
        return normalized;
    }

    private String resolveDatasetPurpose(String datasetPurpose) {
        String normalized = normalizeOptionalCode(datasetPurpose);
        return normalized == null ? DEFAULT_DATASET_PURPOSE : normalized;
    }

    private boolean resolveFeatureEnabled(Boolean featureEnabled) {
        if (featureEnabled == null) {
            return DEFAULT_FEATURE_ENABLED;
        }
        return featureEnabled;
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
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return value;
    }

    private Instant normalizeTimestamp(Object value) {
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
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Instant.parse(trimmed);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
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

    private Map<String, List<Document>> partitionRawRowsByEquipment(List<Document> rawRows) {
        Map<String, List<Document>> partitioned = new LinkedHashMap<>();
        for (Document rawRow : rawRows) {
            String equipmentId = canonicalizeEquipmentId(asString(
                    firstNonBlank(rawRow.get(EQUIPMENT_FIELD), rawRow.get("equipment_id")),
                    null
            ));
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
        String normalized = normalizeOptionalCode(value == null ? null : value.toString());
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

    private record DatasetSelection(
            String datasetKey,
            String datasetName,
            String datasetDisplayName,
            String sourceCollection
    ) {
    }
}