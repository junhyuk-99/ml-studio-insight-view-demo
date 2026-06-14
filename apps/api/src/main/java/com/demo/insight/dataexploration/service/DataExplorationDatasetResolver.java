package com.demo.insight.dataexploration.service;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.dataexploration.dto.DataExplorationDatasetOptionDto;
import com.demo.insight.dataexploration.repository.DataExplorationDatasetConfigRepository;
import com.demo.insight.equipment.service.EquipmentMasterService;
import com.demo.insight.preprocess.domain.DataTypeDatasetDocument;
import com.demo.insight.preprocess.repository.DataTypeDatasetRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class DataExplorationDatasetResolver {

    private static final Logger log = LoggerFactory.getLogger(DataExplorationDatasetResolver.class);

    private static final String ACTIVE_YN = "Y";
    private static final String LEGACY_GLOBAL_DATASET_KEY = "demo_hmi_all_default_v1";
    private static final String DEFAULT_DATASET_KEY = "DEMO_DATASET_MANUFACTURING_AI";
    private static final String NORMALIZED_DEFAULT_DATASET_KEY = "demo_dataset_manufacturing_ai";
    private static final String DEFAULT_DATASET_NAME = "Demo HMI Dataset";
    private static final String DEFAULT_DISPLAY_NAME = "Demo HMI Dataset";
    private static final String DEFAULT_SOURCE_COLLECTION = "THISHMIDATA";
    private static final String DEFAULT_DATASET_PURPOSE = "FEATURE_SOURCE";
    private static final String REQUIRED_TYPE_CODE = "DATABASE";
    private static final String REQUIRED_DTL_CODE = "MONGODB";
    private static final String REQUIRED_DATASET_PURPOSE = "FEATURE_SOURCE";
    private static final Pattern COLLECTION_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private final DataTypeDatasetRepository dataTypeDatasetRepository;
    private final DataExplorationDatasetConfigRepository datasetConfigRepository;
    private final DynamicSchemaResolver schemaResolver;
    private final EquipmentMasterService equipmentMasterService;
    private final boolean collectionApprovalCheckEnabled;

    public DataExplorationDatasetResolver(
            DataTypeDatasetRepository dataTypeDatasetRepository,
            DataExplorationDatasetConfigRepository datasetConfigRepository,
            DynamicSchemaResolver schemaResolver,
            EquipmentMasterService equipmentMasterService,
            @Value("${app.data-exploration.collection-approval-check-enabled:true}")
            boolean collectionApprovalCheckEnabled
    ) {
        this.dataTypeDatasetRepository = dataTypeDatasetRepository;
        this.datasetConfigRepository = datasetConfigRepository;
        this.schemaResolver = schemaResolver;
        this.equipmentMasterService = equipmentMasterService;
        this.collectionApprovalCheckEnabled = collectionApprovalCheckEnabled;
        log.info(
                "Data exploration collection approval check enabled: {}",
                this.collectionApprovalCheckEnabled
        );
    }

    public List<DataExplorationDatasetOptionDto> getActiveDatasets() {
        List<DataTypeDatasetDocument> datasetDocuments =
                dataTypeDatasetRepository.findByUseFlagOrderByTypeCodeAscDtlCodeAscSortNoAsc(ACTIVE_YN);

        List<DataExplorationDatasetOptionDto> options = new ArrayList<>();
        for (DataTypeDatasetDocument datasetDocument : datasetDocuments) {
            DatasetContext context = toDatasetContext(datasetDocument, false, true);
            if (context == null) {
                continue;
            }
            options.add(toOption(context));
        }

        if (options.isEmpty()) {
            DatasetContext fallbackContext = resolveFirstOperationalDatasetContext(true);
            if (fallbackContext != null) {
                options.add(toOption(fallbackContext));
            }
        }

        if (options.isEmpty() && isApprovedCollection(DEFAULT_SOURCE_COLLECTION)) {
            options.add(toOption(defaultFallbackContext(resolveDefaultOperationalDatasetKey())));
        }

        return List.copyOf(options);
    }

    public DatasetContext resolveDatasetContext(String requestedDatasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(requestedDatasetKey);
        if (normalizedDatasetKey != null) {
            if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(normalizedDatasetKey)
                    || equipmentMasterService.isLegacyGlobalDatasetKey(normalizedDatasetKey)) {
                throw new IllegalArgumentException("Unsupported dataset_key for data exploration: " + normalizedDatasetKey);
            }
            DatasetContext explicitContext = resolveContextByDatasetKey(canonicalDatasetKey(normalizedDatasetKey), false, true);
            if (explicitContext != null) {
                return explicitContext;
            }
            throw new IllegalArgumentException(
                    "Active FEATURE_SOURCE dataset not found or inactive(use_flag='Y') for datasetKey: "
                            + normalizedDatasetKey
            );
        }

        DatasetContext fallbackContext = resolveContextByDatasetKey(resolveDefaultOperationalDatasetKey(), true, true);
        if (fallbackContext != null) {
            log.warn(
                    "datasetKey is missing. Falling back to default datasetKey={} and sourceCollection={}.",
                    fallbackContext.datasetKey(),
                    fallbackContext.sourceCollection()
            );
            return fallbackContext;
        }

        DatasetContext firstOperationalContext = resolveFirstOperationalDatasetContext(true);
        if (firstOperationalContext != null) {
            log.warn(
                    "datasetKey is missing. Falling back to first active FEATURE_SOURCE datasetKey={} and sourceCollection={}.",
                    firstOperationalContext.datasetKey(),
                    firstOperationalContext.sourceCollection()
            );
            return firstOperationalContext;
        }

        if (!isApprovedCollection(DEFAULT_SOURCE_COLLECTION)) {
            throw new IllegalArgumentException(
                    "Fallback source collection is not approved: " + DEFAULT_SOURCE_COLLECTION
            );
        }
        return defaultFallbackContext(resolveDefaultOperationalDatasetKey());
    }

    public boolean isSupervisedDataset(DatasetContext datasetContext) {
        if (datasetContext == null) {
            return false;
        }

        String datasetPurpose = normalizeLower(datasetContext.datasetPurpose());
        return datasetPurpose != null
                && (datasetPurpose.contains("supervised") || datasetPurpose.contains("label"));
    }

    private DatasetContext resolveContextByDatasetKey(
            String datasetKey,
            boolean fallbackUsed,
            boolean requireOperationalDataset
    ) {
        if (datasetKey == null) {
            return null;
        }

        DataTypeDatasetDocument datasetDocument =
                dataTypeDatasetRepository.findByDatasetKeyAndUseFlag(datasetKey, ACTIVE_YN);
        return toDatasetContext(datasetDocument, fallbackUsed, requireOperationalDataset);
    }

    private DatasetContext resolveFirstOperationalDatasetContext(boolean fallbackUsed) {
        List<DataTypeDatasetDocument> datasetDocuments =
                dataTypeDatasetRepository.findByUseFlagOrderByTypeCodeAscDtlCodeAscSortNoAsc(ACTIVE_YN);
        for (DataTypeDatasetDocument datasetDocument : datasetDocuments) {
            DatasetContext context = toDatasetContext(datasetDocument, fallbackUsed, true);
            if (context != null) {
                return context;
            }
        }
        return null;
    }

    private DataExplorationDatasetOptionDto toOption(DatasetContext context) {
        return new DataExplorationDatasetOptionDto(
                context.datasetKey(),
                context.datasetName(),
                context.displayName(),
                context.typeCode(),
                context.dtlCode(),
                context.sourceCollection(),
                context.datasetPurpose(),
                context.featureEnabled(),
                context.sortNo()
        );
    }

    private DatasetContext toDatasetContext(
            DataTypeDatasetDocument datasetDocument,
            boolean fallbackUsed,
            boolean requireOperationalDataset
    ) {
        if (datasetDocument == null) {
            return null;
        }

        String datasetKey = normalizeText(datasetDocument.getDatasetKey());
        if (datasetKey == null) {
            return null;
        }

        if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)
                || equipmentMasterService.isLegacyGlobalDatasetKey(datasetKey)) {
            return null;
        }

        if (requireOperationalDataset && !isOperationalFeatureSourceDataset(datasetDocument)) {
            return null;
        }

        Document datasetConfig = datasetConfigRepository.findActiveByDatasetKey(datasetKey);
        if (datasetConfig == null) {
            return null;
        }

        String sourceCollection = resolveApprovedCollection(resolveSourceCollection(datasetConfig, datasetDocument));
        if (sourceCollection == null) {
            return null;
        }

        String datasetName = defaultIfBlank(datasetDocument.getDatasetName(), sourceCollection);
        String displayName = defaultIfBlank(datasetDocument.getDisplayName(), datasetName);
        String datasetPurpose = defaultIfBlank(datasetDocument.getDatasetPurpose(), DEFAULT_DATASET_PURPOSE);
        Document matchFilter = extractMatchFilter(datasetConfig);
        return new DatasetContext(
                datasetKey,
                datasetName,
                displayName,
                normalizeText(datasetDocument.getTypeCode()),
                normalizeText(datasetDocument.getDtlCode()),
                sourceCollection,
                datasetPurpose,
                datasetDocument.getFeatureEnabled(),
                datasetDocument.getSortNo(),
                fallbackUsed,
                matchFilter
        );
    }

    private DatasetContext defaultFallbackContext(String datasetKey) {
        String resolvedDatasetKey = canonicalDatasetKey(normalizeText(datasetKey));
        if (resolvedDatasetKey == null) {
            resolvedDatasetKey = DEFAULT_DATASET_KEY;
        }
        return new DatasetContext(
                resolvedDatasetKey,
                DEFAULT_DATASET_NAME,
                DEFAULT_DISPLAY_NAME,
                REQUIRED_TYPE_CODE,
                REQUIRED_DTL_CODE,
                DEFAULT_SOURCE_COLLECTION,
                DEFAULT_DATASET_PURPOSE,
                Boolean.TRUE,
                1,
                true,
                new Document()
        );
    }

    private boolean isOperationalFeatureSourceDataset(DataTypeDatasetDocument datasetDocument) {
        if (datasetDocument == null) {
            return false;
        }

        String typeCode = normalizeUpper(datasetDocument.getTypeCode());
        String dtlCode = normalizeUpper(datasetDocument.getDtlCode());
        String datasetPurpose = normalizeUpper(datasetDocument.getDatasetPurpose());
        Boolean featureEnabled = datasetDocument.getFeatureEnabled();

        return REQUIRED_TYPE_CODE.equals(typeCode)
                && REQUIRED_DTL_CODE.equals(dtlCode)
                && REQUIRED_DATASET_PURPOSE.equals(datasetPurpose)
                && Boolean.TRUE.equals(featureEnabled);
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
        return DEFAULT_DATASET_KEY;
    }

    private String canonicalDatasetKey(String datasetKey) {
        String normalized = normalizeText(datasetKey);
        if (normalized == null) {
            return null;
        }
        if (NORMALIZED_DEFAULT_DATASET_KEY.equalsIgnoreCase(normalized)
                || DEFAULT_DATASET_KEY.equalsIgnoreCase(normalized)) {
            return DEFAULT_DATASET_KEY;
        }
        return normalized;
    }

    private String resolveSourceCollection(Document datasetConfig, DataTypeDatasetDocument datasetDocument) {
        String fromConfig = extractString(datasetConfig, "source_collection", "SOURCE_COLLECTION");
        if (fromConfig != null) {
            return fromConfig;
        }
        if (datasetDocument == null) {
            return null;
        }
        return datasetDocument.getSourceCollection();
    }

    private Document extractMatchFilter(Document datasetConfig) {
        if (datasetConfig == null || datasetConfig.isEmpty()) {
            return new Document();
        }

        Object rawMatchFilter = extractValue(datasetConfig, "match_filter", "MATCH_FILTER");
        if (rawMatchFilter instanceof Document document) {
            return cloneDocument(document);
        }
        if (rawMatchFilter instanceof Map<?, ?> mapValue) {
            return toDocument(mapValue);
        }
        return new Document();
    }

    private Object extractValue(Document document, String... keys) {
        if (document == null || document.isEmpty() || keys == null || keys.length == 0) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank() || !document.containsKey(key)) {
                continue;
            }
            Object value = document.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String extractString(Document document, String... keys) {
        Object value = extractValue(document, keys);
        if (value == null) {
            return null;
        }
        return normalizeText(String.valueOf(value));
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
            document.put(String.valueOf(entry.getKey()), cloneValue(entry.getValue()));
        }
        return document;
    }

    private Document cloneDocument(Document source) {
        if (source == null) {
            return new Document();
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

    private String resolveApprovedCollection(String collectionName) {
        String normalized = normalizeText(collectionName);
        if (normalized == null) {
            return null;
        }
        if (!COLLECTION_NAME_PATTERN.matcher(normalized).matches()) {
            log.warn("Invalid source collection name detected in dataset registry: {}", collectionName);
            return null;
        }
        if (!isApprovedCollection(normalized)) {
            log.warn("Unapproved source collection detected in dataset registry: {}", normalized);
            return null;
        }
        return normalized;
    }

    private boolean isApprovedCollection(String collectionName) {
        String normalized = normalizeLower(collectionName);
        if (normalized == null) {
            return false;
        }
        if (!collectionApprovalCheckEnabled) {
            return true;
        }
        return schemaResolver.isApprovedCollection(normalized);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeLower(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalizeText(value);
        return normalized == null ? fallback : normalized;
    }

    public record DatasetContext(
            String datasetKey,
            String datasetName,
            String displayName,
            String typeCode,
            String dtlCode,
            String sourceCollection,
            String datasetPurpose,
            Boolean featureEnabled,
            Integer sortNo,
            boolean fallbackUsed,
            Document matchFilter
    ) {
        public Document copyMatchFilter() {
            if (matchFilter == null || matchFilter.isEmpty()) {
                return new Document();
            }
            return cloneDocumentStatic(matchFilter);
        }

        public Map<String, Object> matchFilterAsMap() {
            if (matchFilter == null || matchFilter.isEmpty()) {
                return Map.of();
            }
            return Map.copyOf(new LinkedHashMap<>(cloneDocumentStatic(matchFilter)));
        }

        private static Document cloneDocumentStatic(Document source) {
            if (source == null) {
                return new Document();
            }
            Document target = new Document();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                target.put(entry.getKey(), cloneValueStatic(entry.getValue()));
            }
            return target;
        }

        private static Object cloneValueStatic(Object value) {
            if (value instanceof Document documentValue) {
                return cloneDocumentStatic(documentValue);
            }
            if (value instanceof Map<?, ?> mapValue) {
                Document document = new Document();
                for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    document.put(String.valueOf(entry.getKey()), cloneValueStatic(entry.getValue()));
                }
                return document;
            }
            if (value instanceof List<?> listValue) {
                List<Object> cloned = new ArrayList<>(listValue.size());
                for (Object item : listValue) {
                    cloned.add(cloneValueStatic(item));
                }
                return cloned;
            }
            return value;
        }
    }
}
