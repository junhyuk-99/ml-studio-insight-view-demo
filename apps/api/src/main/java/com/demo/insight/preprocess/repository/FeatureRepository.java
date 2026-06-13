package com.demo.insight.preprocess.repository;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.mongodb.bulk.BulkWriteResult;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class FeatureRepository {

    private static final String FEATURE_COLLECTION = "thisfeature";
    private static final String TOP_LEVEL_DATASET_KEY_FIELD = "dataset_key";
    private static final String META_DATASET_KEY_PATH = "feature_values.META.dataset_key";
    private static final String META_DATASET_KEY_HASH_PATH = "feature_values.META.dataset_key_hash";

    private final MongoTemplate mongoTemplate;
    private final DynamicSchemaResolver schemaResolver;

    public FeatureRepository(MongoTemplate mongoTemplate, DynamicSchemaResolver schemaResolver) {
        this.mongoTemplate = mongoTemplate;
        this.schemaResolver = schemaResolver;
    }

    public List<Map<String, Object>> findFeatureRowsByDatasetKey(
            String datasetKey,
            int limit,
            boolean compactPreview
    ) {
        return findFeatureRowsByDatasetKey(datasetKey, limit, compactPreview, FEATURE_COLLECTION);
    }

    public List<Map<String, Object>> findFeatureRowsByDatasetKey(
            String datasetKey,
            int limit,
            boolean compactPreview,
            String targetCollection
    ) {
        Query query = new Query();
        applyDatasetCriteria(query, datasetKey);
        query.with(Sort.by(Sort.Direction.ASC, "window_start"));
        query.limit(limit);
        query.fields().exclude("_id");
        if (compactPreview) {
            query.fields().exclude("feature_values.META");
        }

        List<Document> documents = mongoTemplate.find(
                query,
                Document.class,
                resolveTargetCollection(targetCollection)
        );
        List<Map<String, Object>> rows = new ArrayList<>(documents.size());
        for (Document document : documents) {
            rows.add(normalizeDocument(document));
        }
        return rows;
    }

    public int upsertFeatureRows(List<Document> featureRows) {
        return upsertFeatureRows(featureRows, FEATURE_COLLECTION);
    }

    public int upsertFeatureRows(List<Document> featureRows, String targetCollection) {
        if (featureRows.isEmpty()) {
            return 0;
        }

        String resolvedCollection = resolveTargetCollection(targetCollection);
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, resolvedCollection);
        for (Document row : featureRows) {
            String datasetKey = enforceDatasetKey(row);
            Query query = buildUpsertQuery(row, datasetKey);
            Update update = new Update();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                update.setOnInsert(entry.getKey(), entry.getValue());
            }
            bulkOperations.upsert(query, update);
        }

        BulkWriteResult result = bulkOperations.execute();
        return result.getUpserts().size();
    }

    private Query buildUpsertQuery(Document row, String datasetKey) {
        return new Query(
                Criteria.where("window_start").is(row.get("window_start"))
                        .and("window_end").is(row.get("window_end"))
                        .and(TOP_LEVEL_DATASET_KEY_FIELD).is(datasetKey)
        );
    }

    private String enforceDatasetKey(Document row) {
        String datasetKey = schemaResolver.normalizeDatasetKeyString(row.get(TOP_LEVEL_DATASET_KEY_FIELD));
        if (datasetKey == null) {
            datasetKey = extractDatasetKeyFromMeta(row.get("feature_values"));
        }
        if (datasetKey == null) {
            throw new IllegalArgumentException("dataset_key must be a non-empty string.");
        }

        row.put(TOP_LEVEL_DATASET_KEY_FIELD, datasetKey);
        ensureMetaDatasetKey(row, datasetKey);
        return datasetKey;
    }

    private String extractDatasetKeyFromMeta(Object featureValuesObject) {
        if (!(featureValuesObject instanceof Map<?, ?> rawFeatureValues)) {
            return null;
        }
        Object metaObject = rawFeatureValues.get("META");
        if (!(metaObject instanceof Map<?, ?> rawMeta)) {
            return null;
        }
        return schemaResolver.normalizeDatasetKeyString(rawMeta.get("dataset_key"));
    }

    private void ensureMetaDatasetKey(Document row, String datasetKey) {
        if (!(row.get("feature_values") instanceof Map<?, ?> rawFeatureValues)) {
            throw new IllegalArgumentException("feature_values is required.");
        }

        Map<String, Object> featureValues = toMutableMap(rawFeatureValues);
        Map<String, Object> meta = toMutableMap(featureValues.get("META"));
        featureValues.put("META", meta);
        meta.put("dataset_key", datasetKey);

        String datasetKeyHash = schemaResolver.datasetKeyHash(datasetKey);
        if (!datasetKeyHash.isBlank()) {
            meta.put("dataset_key_hash", datasetKeyHash);
        }

        row.put("feature_values", featureValues);
    }

    private void applyDatasetCriteria(Query query, String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return;
        }

        String datasetKeyHash = schemaResolver.datasetKeyHash(normalizedDatasetKey);
        List<Criteria> criteriaParts = new ArrayList<>();
        criteriaParts.add(Criteria.where(TOP_LEVEL_DATASET_KEY_FIELD).is(normalizedDatasetKey));
        criteriaParts.add(Criteria.where(META_DATASET_KEY_PATH).is(normalizedDatasetKey));
        if (!datasetKeyHash.isBlank()) {
            criteriaParts.add(Criteria.where(META_DATASET_KEY_HASH_PATH).is(datasetKeyHash));
        }

        query.addCriteria(new Criteria().orOperator(criteriaParts.toArray(Criteria[]::new)));
    }

    private Map<String, Object> normalizeDocument(Document document) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            row.put(entry.getKey(), schemaResolver.normalizeResponseValue(entry.getValue()));
        }
        return row;
    }

    private String resolveTargetCollection(String targetCollection) {
        if (targetCollection == null || targetCollection.isBlank()) {
            return FEATURE_COLLECTION;
        }
        return targetCollection.trim();
    }

    private Map<String, Object> toMutableMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> mutable = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            mutable.put(entry.getKey().toString(), entry.getValue());
        }
        return mutable;
    }
}
