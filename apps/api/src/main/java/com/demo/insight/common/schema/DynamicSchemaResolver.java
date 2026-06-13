package com.demo.insight.common.schema;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DynamicSchemaResolver {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchemaResolver.class);

    private static final Pattern SCHEMA_SECTION_PATTERN =
            Pattern.compile("(?ms)^\\[mongo\\.schema\\.([A-Za-z0-9_]+)]\\s*(.*?)(?=^\\[|\\z)");
    private static final Pattern APPROVED_SECTION_PATTERN =
            Pattern.compile("(?ms)^\\[mongo\\.approved]\\s*(.*?)(?=^\\[|\\z)");
    private static final Pattern FIELDS_BLOCK_PATTERN = Pattern.compile("(?ms)fields\\s*=\\s*\\[(.*?)]");
    private static final Pattern APPROVED_COLLECTIONS_BLOCK_PATTERN = Pattern.compile("(?ms)collections\\s*=\\s*\\[(.*?)]");
    private static final Pattern RUNTIME_DEFAULT_DATASET_KEY_PATTERN =
            Pattern.compile("(?m)^default_dataset_key\\s*=\\s*\"([^\"]+)\"\\s*$");
    private static final Pattern RUNTIME_DEFAULT_PRIMARY_EQUIPMENT_ID_PATTERN =
            Pattern.compile("(?m)^default_primary_equipment_id\\s*=\\s*\"([^\"]+)\"\\s*$");
    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern NON_DATASET_KEY_CHAR_PATTERN = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern MULTI_UNDERSCORE_PATTERN = Pattern.compile("_+");
    private static final Pattern DATASET_KEY_POLICY_PATTERN = Pattern.compile("^[a-z0-9]+_[a-z0-9]+_[a-z0-9]+_v[1-9][0-9]*$");
    private static final Pattern NON_DATASET_TOKEN_CHAR_PATTERN = Pattern.compile("[^a-z0-9]+");

    private static final String DEFAULT_SOURCE_COLLECTION = "THISHMIDATA";
    private static final String DEFAULT_EQUIPMENT_SCOPE = "all";
    private static final String DEFAULT_POLICY_NAME = "default";
    private static final int DEFAULT_POLICY_VERSION = 1;

    private static final List<String> PREFERRED_DATASET_KEY_COLUMNS = List.of(
            "MCCODE",
            "equipment_id",
            "sensor_id",
            "SOURCE_TYPE_CODE",
            "SOURCE_DTL_CODE",
            "SOURCE_FILE"
    );

    private static final Set<String> DATASET_META_KEY_NAMES = Set.of(
            "dataset_name",
            "dataset_key",
            "dataset_label",
            "source_collection",
            "collection_name"
    );

    private static final Set<String> DEFAULT_META_COLUMNS = Set.of(
            "PRDTIME",
            "timestamp",
            "MCCODE",
            "equipment_id",
            "sensor_id",
            "SOURCE_TYPE_CODE",
            "SOURCE_DTL_CODE",
            "SOURCE_FILE",
            "window_start",
            "window_end",
            "REG_DATE",
            "lot_no",
            "feature_values",
            "selected_columns",
            "input_features",
            "params",
            "run_id",
            "status",
            "reg_date",
            "anomaly_score",
            "is_anomaly",
            "health_index",
            "algo_code",
            "algo_name",
            "window_size"
    );

    private final Path configPath;

    private volatile long cachedConfigLastModifiedMillis = -1L;
    private volatile Map<String, List<String>> configuredCollectionFields = Map.of();
    private volatile Set<String> approvedCollections = Set.of();
    private volatile String runtimeDefaultDatasetKey;
    private volatile String runtimeDefaultPrimaryEquipmentId;

    public DynamicSchemaResolver(
            @Value("${app.codex.config-path:${user.home}/.codex/config.toml}") String codexConfigPath
    ) {
        this.configPath = Paths.get(codexConfigPath);
    }

    public List<String> resolveConfiguredColumns(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            return List.of();
        }
        ensureConfigLoaded();
        return configuredCollectionFields.getOrDefault(collectionName.trim().toLowerCase(Locale.ROOT), List.of());
    }

    public Set<String> resolveApprovedCollections() {
        ensureConfigLoaded();
        return approvedCollections;
    }

    public boolean isApprovedCollection(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            return false;
        }
        ensureConfigLoaded();
        return approvedCollections.contains(collectionName.trim().toLowerCase(Locale.ROOT));
    }

    public String resolveRuntimeDefaultDatasetKey() {
        ensureConfigLoaded();
        return normalizeDatasetKeyString(runtimeDefaultDatasetKey);
    }

    public String resolveRuntimeDefaultPrimaryEquipmentId() {
        ensureConfigLoaded();
        String normalized = normalizeString(runtimeDefaultPrimaryEquipmentId);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    public String buildSggHmiDatasetKeyForEquipment(String equipmentId) {
        String normalizedEquipmentId = normalizeEquipmentDatasetToken(equipmentId);
        if (normalizedEquipmentId == null) {
            return null;
        }
        return "demo_hmi_" + normalizedEquipmentId + "_default_v1";
    }

    public List<String> mergeConfiguredAndDiscoveredColumns(String collectionName, List<Document> documents) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(resolveConfiguredColumns(collectionName));
        if (documents != null) {
            for (Document document : documents) {
                merged.addAll(document.keySet());
            }
        }
        merged.remove("_id");
        return List.copyOf(merged);
    }

    public List<String> resolveMetadataColumns(List<String> availableColumns) {
        if (availableColumns == null || availableColumns.isEmpty()) {
            return List.of();
        }

        List<String> metadataColumns = new ArrayList<>();
        for (String column : availableColumns) {
            if (column == null || column.isBlank()) {
                continue;
            }
            if (DEFAULT_META_COLUMNS.contains(column) || looksLikeMetadataColumn(column)) {
                metadataColumns.add(column);
            }
        }
        return List.copyOf(metadataColumns);
    }

    public List<String> resolveNumericColumns(List<Document> documents, Collection<String> candidateColumns) {
        if (documents == null || documents.isEmpty() || candidateColumns == null || candidateColumns.isEmpty()) {
            return List.of();
        }

        List<String> numericColumns = new ArrayList<>();
        for (String candidateColumn : candidateColumns) {
            if (candidateColumn == null || candidateColumn.isBlank()) {
                continue;
            }

            boolean hasNumericValue = false;
            for (Document document : documents) {
                if (isNumericValue(document.get(candidateColumn))) {
                    hasNumericValue = true;
                    break;
                }
            }

            if (hasNumericValue) {
                numericColumns.add(candidateColumn);
            }
        }
        return List.copyOf(numericColumns);
    }

    public List<String> resolveMeasurementColumns(List<Document> documents, Collection<String> availableColumns) {
        if (availableColumns == null || availableColumns.isEmpty()) {
            return List.of();
        }

        Set<String> metadataColumnSet = new LinkedHashSet<>(resolveMetadataColumns(new ArrayList<>(availableColumns)));
        List<String> numericColumns = resolveNumericColumns(documents, availableColumns);
        return numericColumns.stream()
                .filter(column -> !metadataColumnSet.contains(column))
                .toList();
    }

    public List<String> resolveDatasetKeyColumns(List<String> availableColumns, List<Document> sampleDocuments) {
        if (availableColumns == null || availableColumns.isEmpty()) {
            return List.of();
        }

        List<String> orderedColumns = new ArrayList<>();
        for (String preferredColumn : PREFERRED_DATASET_KEY_COLUMNS) {
            if (!availableColumns.contains(preferredColumn)) {
                continue;
            }
            if (!hasNonBlankValue(sampleDocuments, preferredColumn)) {
                continue;
            }
            orderedColumns.add(preferredColumn);
        }

        if (!orderedColumns.isEmpty()) {
            return List.copyOf(orderedColumns);
        }

        for (String column : availableColumns) {
            if (column == null || column.isBlank()) {
                continue;
            }
            if (DEFAULT_META_COLUMNS.contains(column)
                    && !"equipment_id".equals(column)
                    && !"MCCODE".equals(column)) {
                continue;
            }
            if (!hasNonBlankValue(sampleDocuments, column)) {
                continue;
            }
            if (looksLikeMeasurementColumn(column)) {
                continue;
            }
            orderedColumns.add(column);
        }

        return orderedColumns.stream().limit(3).toList();
    }

    public List<Map<String, String>> collectDatasetKeys(List<Document> documents, List<String> datasetKeyColumns) {
        if (documents == null || documents.isEmpty() || datasetKeyColumns == null || datasetKeyColumns.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, Map<String, String>> uniqueKeys = new LinkedHashMap<>();
        for (Document document : documents) {
            Map<String, String> datasetKey = extractDatasetKeyFromDocument(document, datasetKeyColumns);
            if (datasetKey.isEmpty()) {
                continue;
            }
            uniqueKeys.put(datasetKeyHash(datasetKey), datasetKey);
        }
        return List.copyOf(uniqueKeys.values());
    }

    public Map<String, String> extractDatasetKeyFromDocument(Document document, List<String> datasetKeyColumns) {
        if (document == null || datasetKeyColumns == null || datasetKeyColumns.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, String> key = new LinkedHashMap<>();
        for (String keyColumn : datasetKeyColumns) {
            String normalizedValue = normalizeString(document.get(keyColumn));
            if (normalizedValue == null) {
                continue;
            }
            key.put(keyColumn, normalizedValue);
        }
        return normalizeDatasetKey(key);
    }

    public Map<String, String> extractDatasetKeyFromFeatureDocument(Document featureDocument) {
        if (featureDocument == null) {
            return Map.of();
        }

        Object featureValuesObject = featureDocument.get("feature_values");
        if (featureValuesObject instanceof Map<?, ?> featureValuesMap) {
            Object metaObject = featureValuesMap.get("META");
            if (metaObject instanceof Map<?, ?> metaMap) {
                Object datasetKeyObject = metaMap.get("dataset_key");
                if (datasetKeyObject instanceof Map<?, ?> rawDatasetKeyMap) {
                    LinkedHashMap<String, String> rawDatasetKey = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : rawDatasetKeyMap.entrySet()) {
                        if (entry.getKey() == null) {
                            continue;
                        }
                        rawDatasetKey.put(String.valueOf(entry.getKey()), normalizeString(entry.getValue()));
                    }
                    Map<String, String> normalized = normalizeDatasetKey(rawDatasetKey);
                    if (!normalized.isEmpty()) {
                        return normalized;
                    }
                }
            }
        }

        return extractDatasetKeyFromDocument(featureDocument, PREFERRED_DATASET_KEY_COLUMNS);
    }

    public String extractDatasetKeyStringFromFeatureDocument(Document featureDocument) {
        if (featureDocument == null) {
            return null;
        }

        String topLevelDatasetKey = normalizeDatasetKeyString(featureDocument.get("dataset_key"));
        if (topLevelDatasetKey != null) {
            return topLevelDatasetKey;
        }

        Object featureValuesObject = featureDocument.get("feature_values");
        if (featureValuesObject instanceof Map<?, ?> featureValuesMap) {
            Object metaObject = featureValuesMap.get("META");
            if (metaObject instanceof Map<?, ?> metaMap) {
                String metaDatasetKey = normalizeDatasetKeyString(metaMap.get("dataset_key"));
                if (metaDatasetKey != null) {
                    return metaDatasetKey;
                }
            }
        }

        return null;
    }

    public Map<String, String> mergeLegacyDatasetKey(Map<String, ?> rawDatasetKey, String equipmentId, String sensorId) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (rawDatasetKey != null) {
            merged.putAll(rawDatasetKey);
        }

        if (!merged.containsKey("MCCODE")) {
            merged.put("MCCODE", equipmentId);
        }
        if (!merged.containsKey("equipment_id")) {
            merged.put("equipment_id", equipmentId);
        }
        if (!merged.containsKey("sensor_id")) {
            merged.put("sensor_id", sensorId);
        }

        return normalizeDatasetKey(merged);
    }

    public Map<String, String> normalizeDatasetKey(Map<String, ?> rawDatasetKey) {
        if (rawDatasetKey == null || rawDatasetKey.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, String> normalizedRaw = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : rawDatasetKey.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }

            String value = normalizeString(entry.getValue());
            if (value == null) {
                continue;
            }

            normalizedRaw.put(key, value);
        }

        if (normalizedRaw.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String preferredColumn : PREFERRED_DATASET_KEY_COLUMNS) {
            if (normalizedRaw.containsKey(preferredColumn)) {
                ordered.put(preferredColumn, normalizedRaw.get(preferredColumn));
            }
        }

        normalizedRaw.entrySet().stream()
                .filter(entry -> !ordered.containsKey(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));

        return Map.copyOf(ordered);
    }

    public boolean isQueryableDatasetKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (DATASET_META_KEY_NAMES.contains(normalized)) {
            return false;
        }

        return !normalized.startsWith("_");
    }

    public String coerceDatasetKeyString(Object rawDatasetKey) {
        if (rawDatasetKey == null) {
            return null;
        }
        if (rawDatasetKey instanceof String rawTextValue) {
            String normalized = rawTextValue.trim();
            return normalized.isEmpty() ? null : normalized;
        }
        if (rawDatasetKey instanceof Map<?, ?> rawMapValue) {
            Object nestedDatasetKey = rawMapValue.get("dataset_key");
            if (nestedDatasetKey != null) {
                return coerceDatasetKeyString(nestedDatasetKey);
            }
            Object nestedDatasetName = rawMapValue.get("dataset_name");
            return normalizeString(nestedDatasetName);
        }
        return normalizeString(rawDatasetKey);
    }

    public String normalizeDatasetKeyString(Object rawDatasetKey) {
        String candidate = coerceDatasetKeyString(rawDatasetKey);
        if (candidate == null) {
            return null;
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty() || "{}".equals(trimmed) || "[]".equals(trimmed)) {
            return null;
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return null;
        }

        String lowered = trimmed.toLowerCase(Locale.ROOT);
        String normalized = NON_DATASET_KEY_CHAR_PATTERN.matcher(lowered).replaceAll("_");
        normalized = MULTI_UNDERSCORE_PATTERN.matcher(normalized).replaceAll("_");
        normalized = trimUnderscore(normalized);
        return normalized.isEmpty() ? null : normalized;
    }

    public boolean isDatasetKeyPolicyFormat(String datasetKey) {
        String normalized = normalizeDatasetKeyString(datasetKey);
        return normalized != null && DATASET_KEY_POLICY_PATTERN.matcher(normalized).matches();
    }

    public String buildPolicyDatasetKey(
            String sourceCollection,
            String equipmentScope,
            String policyName,
            Integer version
    ) {
        String normalizedSourceCollection = normalizeDatasetToken(sourceCollection, DEFAULT_SOURCE_COLLECTION);
        String normalizedEquipmentScope = normalizeDatasetToken(equipmentScope, DEFAULT_EQUIPMENT_SCOPE);
        String normalizedPolicyName = normalizeDatasetToken(policyName, DEFAULT_POLICY_NAME);
        int normalizedVersion = version == null || version <= 0 ? DEFAULT_POLICY_VERSION : version;
        return normalizedSourceCollection
                + "_"
                + normalizedEquipmentScope
                + "_"
                + normalizedPolicyName
                + "_v"
                + normalizedVersion;
    }

    public String resolveEquipmentScopeFromDatasetKey(String datasetKey) {
        String normalizedDatasetKey = normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return DEFAULT_EQUIPMENT_SCOPE;
        }

        java.util.regex.Matcher demoEquipmentMatcher = java.util.regex.Pattern
                .compile(".*_demo_mc_([0-9]{3})_.*")
                .matcher(normalizedDatasetKey);
        if (demoEquipmentMatcher.matches()) {
            return "DEMO-MC-" + demoEquipmentMatcher.group(1);
        }

        String[] tokens = normalizedDatasetKey.split("_");
        if (tokens.length < 4) {
            return DEFAULT_EQUIPMENT_SCOPE;
        }

        String versionToken = tokens[tokens.length - 1];
        if (!versionToken.matches("v[1-9][0-9]*")) {
            return DEFAULT_EQUIPMENT_SCOPE;
        }

        String equipmentToken = tokens[tokens.length - 3];
        String normalizedScope = normalizeDatasetToken(equipmentToken, DEFAULT_EQUIPMENT_SCOPE);
        return normalizedScope == null ? DEFAULT_EQUIPMENT_SCOPE : normalizedScope;
    }

    private String normalizeEquipmentDatasetToken(String equipmentId) {
        String normalized = normalizeString(equipmentId);
        if (normalized == null) {
            return null;
        }

        java.util.regex.Matcher demoEquipmentMatcher = java.util.regex.Pattern
                .compile("(?i)^DEMO-MC-?([0-9]{3})$")
                .matcher(normalized);
        if (demoEquipmentMatcher.matches()) {
            return "demo_mc_" + demoEquipmentMatcher.group(1);
        }

        return normalizeDatasetToken(normalized, null);
    }

    public String resolveDatasetName(String datasetName, String sourceCollection) {
        String normalizedDatasetName = normalizeString(datasetName);
        if (normalizedDatasetName != null) {
            return normalizedDatasetName;
        }
        return normalizeDatasetToken(sourceCollection, DEFAULT_SOURCE_COLLECTION);
    }

    public String datasetKeyHash(String datasetKey) {
        String normalized = normalizeDatasetKeyString(datasetKey);
        if (normalized == null) {
            return "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 hash algorithm is not available.", exception);
        }
    }

    public String buildDatasetLabel(String datasetKey, String datasetName) {
        String normalizedDatasetKey = normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return "(unclassified dataset)";
        }

        String normalizedDatasetName = normalizeString(datasetName);
        if (normalizedDatasetName == null || normalizedDatasetName.equalsIgnoreCase(normalizedDatasetKey)) {
            return normalizedDatasetKey;
        }
        return normalizedDatasetKey + " (" + normalizedDatasetName + ")";
    }

    public String datasetKeyHash(Map<String, ?> datasetKey) {
        Map<String, String> normalized = normalizeDatasetKey(datasetKey);
        if (normalized.isEmpty()) {
            return "";
        }

        String canonical = normalized.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 hash algorithm is not available.", exception);
        }
    }

    public String buildDatasetLabel(Map<String, ?> datasetKey) {
        Map<String, String> normalized = normalizeDatasetKey(datasetKey);
        if (normalized.isEmpty()) {
            return "(unclassified dataset)";
        }
        return normalized.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + " | " + right)
                .orElse("(unclassified dataset)");
    }

    public Map<String, String> buildColumnLabels(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        for (String column : columns) {
            if (column == null || column.isBlank()) {
                continue;
            }
            labels.put(column, resolveDisplayLabel(column));
        }
        return Map.copyOf(labels);
    }

    public Object normalizeResponseValue(Object value) {
        if (value instanceof Date dateValue) {
            return dateValue.toInstant().toString();
        }
        if (value instanceof Instant instantValue) {
            return instantValue.toString();
        }
        if (value instanceof Document documentValue) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : documentValue.entrySet()) {
                normalized.put(entry.getKey(), normalizeResponseValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Map<?, ?> mapValue) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeResponseValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List<?> listValue) {
            List<Object> normalized = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                normalized.add(normalizeResponseValue(item));
            }
            return normalized;
        }
        return value;
    }

    private synchronized void ensureConfigLoaded() {
        if (!Files.exists(configPath)) {
            configuredCollectionFields = Map.of();
            approvedCollections = Set.of();
            runtimeDefaultDatasetKey = null;
            runtimeDefaultPrimaryEquipmentId = null;
            cachedConfigLastModifiedMillis = -1L;
            return;
        }

        long lastModifiedMillis;
        try {
            lastModifiedMillis = Files.getLastModifiedTime(configPath).toMillis();
        } catch (IOException exception) {
            log.warn("Failed to check config.toml timestamp. path={}", configPath, exception);
            return;
        }

        if (lastModifiedMillis == cachedConfigLastModifiedMillis && !configuredCollectionFields.isEmpty()) {
            return;
        }

        try {
            String tomlContent = Files.readString(configPath, StandardCharsets.UTF_8);
            configuredCollectionFields = parseConfiguredCollectionFields(tomlContent);
            approvedCollections = parseApprovedCollections(tomlContent);
            runtimeDefaultDatasetKey = parseRuntimeDefaultDatasetKey(tomlContent);
            runtimeDefaultPrimaryEquipmentId = parseRuntimeDefaultPrimaryEquipmentId(tomlContent);
            cachedConfigLastModifiedMillis = lastModifiedMillis;
        } catch (IOException exception) {
            log.warn("Failed to read config.toml. path={}", configPath, exception);
        }
    }

    private Map<String, List<String>> parseConfiguredCollectionFields(String tomlContent) {
        if (tomlContent == null || tomlContent.isBlank()) {
            return Map.of();
        }

        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        Matcher sectionMatcher = SCHEMA_SECTION_PATTERN.matcher(tomlContent);
        while (sectionMatcher.find()) {
            String collectionName = sectionMatcher.group(1);
            String sectionBody = sectionMatcher.group(2);
            if (collectionName == null || sectionBody == null) {
                continue;
            }

            Matcher fieldsMatcher = FIELDS_BLOCK_PATTERN.matcher(sectionBody);
            if (!fieldsMatcher.find()) {
                continue;
            }

            String fieldsBody = fieldsMatcher.group(1);
            LinkedHashSet<String> fields = new LinkedHashSet<>();
            Matcher fieldMatcher = QUOTED_VALUE_PATTERN.matcher(fieldsBody);
            while (fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                if (fieldName != null && !fieldName.isBlank()) {
                    fields.add(fieldName.trim());
                }
            }

            if (!fields.isEmpty()) {
                result.put(collectionName.trim().toLowerCase(Locale.ROOT), List.copyOf(fields));
            }
        }

        return Map.copyOf(result);
    }

    private Set<String> parseApprovedCollections(String tomlContent) {
        if (tomlContent == null || tomlContent.isBlank()) {
            return Set.of();
        }

        Matcher sectionMatcher = APPROVED_SECTION_PATTERN.matcher(tomlContent);
        if (!sectionMatcher.find()) {
            return Set.of();
        }

        String sectionBody = sectionMatcher.group(1);
        if (sectionBody == null || sectionBody.isBlank()) {
            return Set.of();
        }

        Matcher collectionsMatcher = APPROVED_COLLECTIONS_BLOCK_PATTERN.matcher(sectionBody);
        if (!collectionsMatcher.find()) {
            return Set.of();
        }

        String collectionsBody = collectionsMatcher.group(1);
        if (collectionsBody == null || collectionsBody.isBlank()) {
            return Set.of();
        }

        LinkedHashSet<String> collectionNames = new LinkedHashSet<>();
        Matcher valueMatcher = QUOTED_VALUE_PATTERN.matcher(collectionsBody);
        while (valueMatcher.find()) {
            String collectionName = valueMatcher.group(1);
            if (collectionName == null || collectionName.isBlank()) {
                continue;
            }
            collectionNames.add(collectionName.trim().toLowerCase(Locale.ROOT));
        }

        if (collectionNames.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(collectionNames);
    }

    private String parseRuntimeDefaultDatasetKey(String tomlContent) {
        if (tomlContent == null || tomlContent.isBlank()) {
            return null;
        }
        Matcher matcher = RUNTIME_DEFAULT_DATASET_KEY_PATTERN.matcher(tomlContent);
        if (!matcher.find()) {
            return null;
        }
        return normalizeDatasetKeyString(matcher.group(1));
    }

    private String parseRuntimeDefaultPrimaryEquipmentId(String tomlContent) {
        if (tomlContent == null || tomlContent.isBlank()) {
            return null;
        }
        Matcher matcher = RUNTIME_DEFAULT_PRIMARY_EQUIPMENT_ID_PATTERN.matcher(tomlContent);
        if (!matcher.find()) {
            return null;
        }
        String normalized = normalizeString(matcher.group(1));
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private boolean hasNonBlankValue(List<Document> documents, String fieldName) {
        if (documents == null || documents.isEmpty() || fieldName == null || fieldName.isBlank()) {
            return false;
        }

        for (Document document : documents) {
            String normalized = normalizeString(document.get(fieldName));
            if (normalized != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isNumericValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof String textValue) {
            String trimmed = textValue.trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            try {
                Double.parseDouble(trimmed);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private String normalizeString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeDatasetToken(String value, String fallback) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return fallback;
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        String sanitized = NON_DATASET_TOKEN_CHAR_PATTERN.matcher(lowered).replaceAll("");
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private String trimUnderscore(String value) {
        if (value == null) {
            return "";
        }

        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean looksLikeMetadataColumn(String columnName) {
        String lowerName = columnName.toLowerCase(Locale.ROOT);
        return lowerName.contains("time")
                || lowerName.contains("date")
                || lowerName.contains("id")
                || lowerName.startsWith("source_")
                || lowerName.startsWith("window_");
    }

    private boolean looksLikeMeasurementColumn(String columnName) {
        String lowerName = columnName.toLowerCase(Locale.ROOT);
        return lowerName.contains("temp")
                || lowerName.contains("press")
                || lowerName.contains("time")
                || lowerName.contains("rate")
                || lowerName.contains("velocity")
                || lowerName.contains("position")
                || lowerName.contains("rpm")
                || lowerName.contains("cycle")
                || lowerName.contains("cushion")
                || lowerName.contains("flow");
    }

    private String resolveDisplayLabel(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return "";
        }

        if (columnName.contains("_")) {
            return columnName;
        }

        return columnName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}
