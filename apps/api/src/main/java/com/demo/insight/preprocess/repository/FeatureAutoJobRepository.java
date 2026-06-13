package com.demo.insight.preprocess.repository;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class FeatureAutoJobRepository {

    private static final String COLLECTION = "tmst_feature_mst";
    private static final String DATASET_CONFIG_COLLECTION = "tmst_dataset_config";
    private static final String DATASET_KEY_FIELD = "dataset_key";
    private static final String SOURCE_COLLECTION_FIELD = "source_collection";
    private static final String TARGET_COLLECTION_FIELD = "target_collection";
    private static final String SCHEDULER_ENABLED_FIELD = "scheduler_enabled";
    private static final String USE_YN_ACTIVE = "Y";

    private final MongoTemplate mongoTemplate;

    public FeatureAutoJobRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Document> findAllJobs() {
        Query query = new Query(baseFeaturePolicyCriteria());
        query.with(Sort.by(Sort.Direction.ASC, DATASET_KEY_FIELD));
        return mongoTemplate.find(query, Document.class, COLLECTION);
    }

    public List<Document> findActiveJobs() {
        Query query = Query.query(
                new Criteria().andOperator(
                        baseFeaturePolicyCriteria(),
                        Criteria.where("use_yn").is(USE_YN_ACTIVE)
                )
        );
        query.with(Sort.by(Sort.Direction.ASC, DATASET_KEY_FIELD));
        return mongoTemplate.find(query, Document.class, COLLECTION);
    }

    public List<Document> findActiveSchedulerJobs() {
        Query query = Query.query(
                new Criteria().andOperator(
                        baseFeaturePolicyCriteria(),
                        Criteria.where("use_yn").is(USE_YN_ACTIVE),
                        Criteria.where(SCHEDULER_ENABLED_FIELD).is(true)
                )
        );
        query.with(Sort.by(Sort.Direction.ASC, DATASET_KEY_FIELD));
        return mongoTemplate.find(query, Document.class, COLLECTION);
    }

    public Document findByJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }

        Query query = buildJobIdentityQuery(jobId.trim());
        return mongoTemplate.findOne(query, Document.class, COLLECTION);
    }

    public Document findDatasetConfigByDatasetKey(String datasetKey) {
        if (datasetKey == null || datasetKey.isBlank()) {
            return null;
        }

        Query query = Query.query(Criteria.where(DATASET_KEY_FIELD).is(datasetKey.trim()));
        query.with(Sort.by(Sort.Direction.DESC, "updated_at").and(Sort.by(Sort.Direction.DESC, "reg_date")));
        query.limit(1);
        return mongoTemplate.findOne(query, Document.class, DATASET_CONFIG_COLLECTION);
    }

    public Document upsertJob(String jobId, String sourceCollection, Map<String, Object> fields) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("dataset_key is required.");
        }

        String normalizedJobId = jobId.trim();
        Query query = buildJobIdentityQuery(normalizedJobId);
        Update update = buildUpsertUpdate(fields);
        String now = Instant.now().toString();

        // Upsert identity is dataset_key.
        // Keep dataset_key and source_collection as top-level fields.
        update.set(DATASET_KEY_FIELD, normalizedJobId);
        update.set(SOURCE_COLLECTION_FIELD, sourceCollection);
        update.set("upd_date", now);
        update.setOnInsert("reg_date", now);

        mongoTemplate.upsert(query, update, COLLECTION);
        return findByJobId(normalizedJobId);
    }

    public Document updateJob(String jobId, Map<String, Object> fields) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }

        Query query = buildJobIdentityQuery(jobId.trim());
        Update update = buildUpdate(fields);
        update.set("upd_date", Instant.now().toString());

        mongoTemplate.updateFirst(query, update, COLLECTION);
        return findByJobId(jobId);
    }

    private Update buildUpdate(Map<String, Object> fields) {
        Update update = new Update();
        if (fields == null || fields.isEmpty()) {
            return update;
        }

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            validateNoNestedDatasetKeyPath(entry.getKey());
            update.set(entry.getKey(), entry.getValue());
        }
        return update;
    }

    private Update buildUpsertUpdate(Map<String, Object> fields) {
        Update update = new Update();
        if (fields == null || fields.isEmpty()) {
            update.setOnInsert(TARGET_COLLECTION_FIELD, "thisfeature");
            update.setOnInsert(SCHEDULER_ENABLED_FIELD, true);
            return update;
        }

        boolean hasTargetCollection = false;
        boolean hasSchedulerEnabled = false;

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }

            validateNoNestedDatasetKeyPath(key);

            if (DATASET_KEY_FIELD.equals(key) || SOURCE_COLLECTION_FIELD.equals(key)) {
                // Controlled by repository to avoid duplicate/conflicting update paths.
                continue;
            }

            if (TARGET_COLLECTION_FIELD.equals(key)) {
                hasTargetCollection = true;
            } else if (SCHEDULER_ENABLED_FIELD.equals(key)) {
                hasSchedulerEnabled = true;
            }

            update.set(key, entry.getValue());
        }

        if (!hasTargetCollection) {
            update.setOnInsert(TARGET_COLLECTION_FIELD, "thisfeature");
        }
        if (!hasSchedulerEnabled) {
            update.setOnInsert(SCHEDULER_ENABLED_FIELD, true);
        }
        return update;
    }

    private Query buildJobIdentityQuery(String datasetKey) {
        return Query.query(
                Criteria.where(DATASET_KEY_FIELD).is(datasetKey)
        );
    }

    private void validateNoNestedDatasetKeyPath(String fieldPath) {
        String normalizedPath = fieldPath.trim();
        if (normalizedPath.startsWith(DATASET_KEY_FIELD + ".")) {
            throw new IllegalArgumentException("dataset_key must be stored as a top-level string field.");
        }
    }

    private Criteria baseFeaturePolicyCriteria() {
        return new Criteria().andOperator(
                Criteria.where(DATASET_KEY_FIELD).exists(true),
                Criteria.where(TARGET_COLLECTION_FIELD).exists(true)
        );
    }
}
