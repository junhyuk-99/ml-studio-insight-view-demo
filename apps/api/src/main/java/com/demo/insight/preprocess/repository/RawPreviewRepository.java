package com.demo.insight.preprocess.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.demo.insight.common.schema.DynamicSchemaResolver;

@Repository
public class RawPreviewRepository {

    private static final String RAW_COLLECTION = "THISHMIDATA";
    private static final String TIMESTAMP_FIELD = "PRDTIME";
    private static final String EQUIPMENT_FIELD = "MCCODE";
    private static final Set<String> ROOT_QUERY_OPERATORS = Set.of("$and", "$or", "$nor", "$expr");

    private final MongoTemplate mongoTemplate;
    private final DynamicSchemaResolver schemaResolver;

    public RawPreviewRepository(MongoTemplate mongoTemplate, DynamicSchemaResolver schemaResolver) {
        this.mongoTemplate = mongoTemplate;
        this.schemaResolver = schemaResolver;
    }

    public RawPreviewResult findPreviewRows(
            String sourceCollection,
            Map<String, Object> datasetFilter,
            String typeCode,
            String dtlCode,
            String from,
            String to,
            String equipmentId,
            int limit
    ) {
        String resolvedCollection = resolveCollectionName(sourceCollection);

        Document datasetQueryDocument = buildDatasetQueryDocument(datasetFilter);
        Document sourceQueryDocument = buildSourceQueryDocument(typeCode, dtlCode, from, to, equipmentId);
        Document mergedQueryDocument = mergeQueryDocuments(datasetQueryDocument, sourceQueryDocument);

        Query query = new BasicQuery(mergedQueryDocument);
        query.with(Sort.by(Sort.Direction.DESC, TIMESTAMP_FIELD).and(Sort.by(Sort.Direction.DESC, "_id")));
        query.limit(limit);
        query.fields().exclude("_id");

        List<Document> documents = mongoTemplate.find(query, Document.class, resolvedCollection);
        List<String> availableColumns = schemaResolver.mergeConfiguredAndDiscoveredColumns(resolvedCollection, documents);
        List<String> metadataColumns = schemaResolver.resolveMetadataColumns(availableColumns);
        List<String> numericColumns = schemaResolver.resolveNumericColumns(documents, availableColumns);
        List<String> datasetKeyColumns = schemaResolver.resolveDatasetKeyColumns(availableColumns, documents);
        List<Map<String, String>> datasetKeys = schemaResolver.collectDatasetKeys(documents, datasetKeyColumns);

        List<Map<String, Object>> rows = new ArrayList<>(documents.size());
        for (Document document : documents) {
            rows.add(toProjectedRow(document, availableColumns));
        }

        return new RawPreviewResult(
                availableColumns,
                metadataColumns,
                numericColumns,
                schemaResolver.buildColumnLabels(availableColumns),
                datasetKeyColumns,
                datasetKeys,
                rows
        );
    }

    public RawPreviewResult findPreviewRows(
            String sourceCollection,
            String typeCode,
            String dtlCode,
            String from,
            String to,
            String equipmentId,
            int limit
    ) {
        return findPreviewRows(
                sourceCollection,
                Map.of(),
                typeCode,
                dtlCode,
                from,
                to,
                equipmentId,
                limit
        );
    }

    public RawPreviewResult findPreviewRows(
            String typeCode,
            String dtlCode,
            String from,
            String to,
            String equipmentId,
            int limit
    ) {
        return findPreviewRows(
                RAW_COLLECTION,
                Map.of(),
                typeCode,
                dtlCode,
                from,
                to,
                equipmentId,
                limit
        );
    }

    public List<Document> findRowsByDatasetKey(Map<String, Object> datasetFilter) {
        return findRowsByDatasetKey(RAW_COLLECTION, datasetFilter);
    }

    public List<Document> findRowsByDatasetKey(String sourceCollection, Map<String, Object> datasetFilter) {
        String resolvedCollection = resolveCollectionName(sourceCollection);
        Query query = new BasicQuery(buildDatasetQueryDocument(datasetFilter));

        query.with(Sort.by(Sort.Direction.ASC, TIMESTAMP_FIELD).and(Sort.by(Sort.Direction.ASC, "_id")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, resolvedCollection);
    }

    public List<Document> findRowsByDatasetKeyAfterObjectId(
            Map<String, Object> datasetFilter,
            String lastProcessedRowId,
            int limit
    ) {
        return findRowsByDatasetKeyAfterObjectId(RAW_COLLECTION, datasetFilter, lastProcessedRowId, limit);
    }

    public List<Document> findRowsByDatasetKeyAfterObjectId(
            String sourceCollection,
            Map<String, Object> datasetFilter,
            String lastProcessedRowId,
            int limit
    ) {
        String resolvedCollection = resolveCollectionName(sourceCollection);
        Document queryDocument = buildDatasetQueryDocument(datasetFilter);
        Document incrementalCriteria = buildAfterObjectIdCondition(lastProcessedRowId);
        if (incrementalCriteria != null) {
            queryDocument.put("_id", incrementalCriteria);
        }

        Query query = new BasicQuery(queryDocument);
        query.with(Sort.by(Sort.Direction.ASC, "_id"));
        query.limit(Math.max(limit, 1));
        return mongoTemplate.find(query, Document.class, resolvedCollection);
    }

    public long countRowsByDatasetKeyAfterObjectId(Map<String, Object> datasetFilter, String lastProcessedRowId) {
        return countRowsByDatasetKeyAfterObjectId(RAW_COLLECTION, datasetFilter, lastProcessedRowId);
    }

    public long countRowsByDatasetKeyAfterObjectId(
            String sourceCollection,
            Map<String, Object> datasetFilter,
            String lastProcessedRowId
    ) {
        String resolvedCollection = resolveCollectionName(sourceCollection);
        Document queryDocument = buildDatasetQueryDocument(datasetFilter);
        Document incrementalCriteria = buildAfterObjectIdCondition(lastProcessedRowId);
        if (incrementalCriteria != null) {
            queryDocument.put("_id", incrementalCriteria);
        }

        Query query = new BasicQuery(queryDocument);
        return mongoTemplate.count(query, resolvedCollection);
    }

    private Document buildSourceQueryDocument(
            String typeCode,
            String dtlCode,
            String from,
            String to,
            String equipmentId
    ) {
        Document query = new Document();

        if (typeCode != null && !typeCode.isBlank()) {
            query.put("SOURCE_TYPE_CODE", typeCode.trim());
        }

        if (dtlCode != null && !dtlCode.isBlank()) {
            query.put("SOURCE_DTL_CODE", dtlCode.trim());
        }

        if (equipmentId != null && !equipmentId.isBlank()) {
            query.put(EQUIPMENT_FIELD, equipmentId.trim());
        }

        Date fromDate = parseIsoDate(from);
        Date toDate = parseIsoDate(to);
        if (fromDate != null || toDate != null) {
            Document timeQuery = new Document();
            if (fromDate != null) {
                timeQuery.put("$gte", fromDate);
            }
            if (toDate != null) {
                timeQuery.put("$lte", toDate);
            }
            query.put(TIMESTAMP_FIELD, timeQuery);
        }

        return query;
    }

    private Document mergeQueryDocuments(Document datasetQueryDocument, Document sourceQueryDocument) {
        boolean hasDatasetQuery = datasetQueryDocument != null && !datasetQueryDocument.isEmpty();
        boolean hasSourceQuery = sourceQueryDocument != null && !sourceQueryDocument.isEmpty();

        if (!hasDatasetQuery && !hasSourceQuery) {
            return new Document();
        }

        if (hasDatasetQuery && !hasSourceQuery) {
            return datasetQueryDocument;
        }

        if (!hasDatasetQuery) {
            return sourceQueryDocument;
        }

        List<Document> andClauses = new ArrayList<>();
        andClauses.add(datasetQueryDocument);
        andClauses.add(sourceQueryDocument);

        return new Document("$and", andClauses);
    }

    private Document buildDatasetQueryDocument(Map<String, Object> datasetFilter) {
        Document query = new Document();
        if (datasetFilter == null || datasetFilter.isEmpty()) {
            return query;
        }

        for (Map.Entry<String, Object> entry : datasetFilter.entrySet()) {
            String rawKey = entry.getKey();
            if (rawKey == null) {
                continue;
            }

            String key = rawKey.trim();
            if (key.isEmpty()) {
                continue;
            }

            if (key.startsWith("$")) {
                if (!ROOT_QUERY_OPERATORS.contains(key)) {
                    continue;
                }

                Object normalizedOperatorValue = normalizeFilterValue(entry.getValue());
                if (normalizedOperatorValue != null) {
                    query.put(key, normalizedOperatorValue);
                }
                continue;
            }

            if (!schemaResolver.isQueryableDatasetKey(key)) {
                continue;
            }

            Object normalizedValue = normalizeFilterValue(entry.getValue());
            if (normalizedValue == null) {
                continue;
            }

            if (normalizedValue instanceof String text && text.isBlank()) {
                continue;
            }

            if (normalizedValue instanceof Map<?, ?> mapValue && mapValue.isEmpty()) {
                continue;
            }

            query.put(key, normalizedValue);
        }

        return query;
    }

    private Object normalizeFilterValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String textValue) {
            return textValue.trim();
        }

        if (value instanceof Document documentValue) {
            return normalizeFilterMap(documentValue);
        }

        if (value instanceof Map<?, ?> mapValue) {
            return normalizeFilterMap(mapValue);
        }

        if (value instanceof List<?> listValue) {
            List<Object> normalized = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                normalized.add(normalizeFilterValue(item));
            }
            return normalized;
        }

        return value;
    }

    private Document normalizeFilterMap(Map<?, ?> rawMap) {
        Document normalized = new Document();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            normalized.put(entry.getKey().toString(), normalizeFilterValue(entry.getValue()));
        }

        return normalized;
    }

    private Document buildAfterObjectIdCondition(String rowId) {
        if (rowId == null || rowId.isBlank()) {
            return null;
        }

        try {
            return new Document("$gt", new ObjectId(rowId.trim()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<String, Object> toProjectedRow(Document document, List<String> availableColumns) {
        Document projected = new Document();
        for (String column : availableColumns) {
            projected.put(column, schemaResolver.normalizeResponseValue(document.get(column)));
        }
        return projected;
    }

    private String resolveCollectionName(String sourceCollection) {
        if (sourceCollection == null) {
            return RAW_COLLECTION;
        }

        String trimmed = sourceCollection.trim();
        return trimmed.isEmpty() ? RAW_COLLECTION : trimmed;
    }

    private Date parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Date.from(Instant.parse(value.trim()));
        } catch (Exception ignored) {
            return null;
        }
    }
}