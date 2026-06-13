package com.demo.insight.modeltrain.repository;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.mongodb.MongoCommandException;
import com.mongodb.bulk.BulkWriteResult;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Repository
public class ModelTrainRepository {

    private static final String MODEL_RUN_COLLECTION = "thismodelrun";
    private static final String MODEL_POLICY_COLLECTION = "tmst_model_policy";
    private static final String MODEL_ACTIVE_COLLECTION = "tmst_model_active";
    private static final String FEATURE_POLICY_COLLECTION = "tmst_feature_mst";
    private static final String DATASET_CONFIG_COLLECTION = "tmst_dataset_config";
    private static final String FEATURE_COLLECTION = "thisfeature";
    private static final String RAW_COLLECTION = "THISHMIDATA";
    private static final String LABELED_RAW_COLLECTION = "thisrawlabeled";
    private static final String ANOMALY_RESULT_COLLECTION = "thisanomalyresult";
    private static final String CLASSIFICATION_RESULT_COLLECTION = "thisclassificationresult";
    private static final String MODEL_EVAL_COLLECTION = "thismodeleval";
    private static final String LEGACY_ANOMALY_INDEX = "ix_thisanomaly_eq_sensor_date";
    private static final String ANOMALY_INDEX_DATASET_EQUIPMENT_WINDOW = "idx_anomaly_dataset_equipment_window";
    private static final String ANOMALY_INDEX_RUN_ID = "idx_anomaly_runid";
    private static final String ANOMALY_INDEX_CAUSE_GENERATED = "idx_anomaly_cause_generated";
    private static final String ANOMALY_INDEX_ALERT_GENERATED = "idx_anomaly_alert_generated";
    private static final String CLASSIFICATION_INDEX_RUN_DATASET_SOURCE_SPLIT = "ix_thisclassification_run_dataset_source_split";
    private static final String MODELEVAL_INDEX_RUN_DATASET_ALGO_REGDATE = "ix_thismodeleval_run_dataset_algo_reg_date";
    private static final String FEATURE_DATASET_KEY_PATH = "dataset_key";
    private static final String FEATURE_META_DATASET_KEY_PATH = "feature_values.META.dataset_key";
    private static final String FEATURE_DATASET_KEY_HASH_PATH = "feature_values.META.dataset_key_hash";
    private static final String DOC_TYPE_FIELD = "doc_type";
    private static final String DOC_TYPE_RUN = "RUN";
    private static final String USE_FLAG_FIELD = "use_flag";
    private static final String ACTIVE_USE_FLAG = "Y";
    private static final String ACTIVE_USE_YN = "Y";
    private static final String NORMAL_STATUS = "NORMAL";
    private static final String CRITICAL_STATUS = "CRITICAL";
    private static final String NO_DATA_STATUS = "NO_DATA";
    private static final String RAW_TIMESTAMP_FIELD = "PRDTIME";
    private static final String RAW_EQUIPMENT_FIELD = "MCCODE";

    private final MongoTemplate mongoTemplate;
    private final DynamicSchemaResolver schemaResolver;
    private final AtomicBoolean anomalyCollectionPrepared = new AtomicBoolean(false);
    private final AtomicBoolean classificationCollectionPrepared = new AtomicBoolean(false);
    private final AtomicBoolean modelEvalCollectionPrepared = new AtomicBoolean(false);

    public ModelTrainRepository(MongoTemplate mongoTemplate, DynamicSchemaResolver schemaResolver) {
        this.mongoTemplate = mongoTemplate;
        this.schemaResolver = schemaResolver;
    }

    public boolean existsRunId(String runId) {
        Query query = new Query(
                Criteria.where("run_id").is(runId)
                        .andOperator(runDocTypeCriteria())
        );
        return mongoTemplate.exists(query, MODEL_RUN_COLLECTION);
    }

    public void insertModelRun(Document modelRunDocument) {
        modelRunDocument.putIfAbsent(DOC_TYPE_FIELD, DOC_TYPE_RUN);
        mongoTemplate.insert(modelRunDocument, MODEL_RUN_COLLECTION);
    }

    public Document findModelRunByRunId(String runId) {
        Query query = new Query(
                Criteria.where("run_id").is(runId)
                        .andOperator(runDocTypeCriteria())
        );
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public List<Document> findRecentModelRuns(List<String> algoCodes, int limit) {
        return findRecentModelRuns(algoCodes, null, null, false, limit);
    }

    public List<Document> findRecentModelRuns(
            List<String> algoCodes,
            String datasetKey,
            String equipmentId,
            boolean successOnly,
            int limit
    ) {
        List<String> normalizedAlgoCodes = new ArrayList<>();
        if (algoCodes != null) {
            for (String algoCode : algoCodes) {
                if (algoCode == null) {
                    continue;
                }
                String normalized = algoCode.trim();
                if (!normalized.isEmpty()) {
                    normalizedAlgoCodes.add(normalized);
                }
            }
        }

        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(runDocTypeCriteria());

        if (!normalizedAlgoCodes.isEmpty()) {
            criteriaList.add(Criteria.where("algo_code").in(normalizedAlgoCodes));
        }

        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey != null) {
            criteriaList.add(Criteria.where("dataset_key").is(normalizedDatasetKey));
        }

        String normalizedEquipmentId = normalizeOptionalText(equipmentId);
        if (normalizedEquipmentId != null) {
            criteriaList.add(buildEquipmentCriteria(normalizedEquipmentId));
        }

        if (successOnly) {
            criteriaList.add(Criteria.where("status").is("SUCCESS"));
        }

        Query query = new Query();
        if (criteriaList.size() == 1) {
            query.addCriteria(criteriaList.get(0));
        } else {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        query.with(Sort.by(Sort.Direction.DESC, "reg_date").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.limit(Math.max(limit, 1));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public Document findLatestModelRunByDatasetAndAlgo(String datasetKey, String equipmentId, String algoCode) {
        String normalizedAlgoCode = normalizeOptionalText(algoCode);
        if (normalizedAlgoCode == null) {
            return null;
        }

        List<Criteria> criteriaList = new ArrayList<>();

        criteriaList.add(runDocTypeCriteria());
        criteriaList.add(Criteria.where("algo_code").is(normalizedAlgoCode));

        String normalizedDatasetKey = normalizeOptionalText(datasetKey);
        if (normalizedDatasetKey != null) {
            criteriaList.add(Criteria.where("dataset_key").is(normalizedDatasetKey));
        }

        String normalizedEquipmentId = normalizeOptionalText(equipmentId);
        if (normalizedEquipmentId != null) {
            criteriaList.add(buildEquipmentCriteria(normalizedEquipmentId));
        }

        Query query = new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

        query.with(Sort.by(Sort.Direction.DESC, "reg_date"));
        query.limit(1);
        query.fields().exclude("_id");

        return mongoTemplate.findOne(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public Document findLatestModelRunByDatasetPolicyAndAlgo(String datasetKey, String policyId, String algoCode) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        String normalizedPolicyId = normalizeOptionalText(policyId);
        String normalizedAlgoCode = normalizeOptionalText(algoCode);

        Query query = new Query(runDocTypeCriteria());
        if (normalizedDatasetKey != null) {
            query.addCriteria(Criteria.where("dataset_key").is(normalizedDatasetKey));
        }
        if (normalizedPolicyId != null) {
            query.addCriteria(Criteria.where("policy_id").is(normalizedPolicyId));
        }
        if (normalizedAlgoCode != null) {
            query.addCriteria(Criteria.where("algo_code").is(normalizedAlgoCode));
        }

        query.with(Sort.by(Sort.Direction.DESC, "reg_date").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public Document findLatestModelRunByDatasetKey(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return null;
        }

        Query query = new Query(
                Criteria.where("dataset_key").is(normalizedDatasetKey)
                        .andOperator(runDocTypeCriteria())
        );
        query.with(Sort.by(Sort.Direction.DESC, "reg_date"));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public void updateModelRunStatus(String runId, String status) {
        Query query = new Query(
                Criteria.where("run_id").is(runId)
                        .andOperator(runDocTypeCriteria())
        );
        Update update = new Update()
                .set("status", status)
                .set("updated_at", new Date());
        mongoTemplate.updateFirst(query, update, MODEL_RUN_COLLECTION);
    }

    public void updateModelRunExecutionInfo(String runId, String status, String message, Map<String, Object> params) {
        Query query = new Query(
                Criteria.where("run_id").is(runId)
                        .andOperator(runDocTypeCriteria())
        );
        Update update = new Update()
                .set("status", status)
                .set("updated_at", new Date());

        if (message == null || message.isBlank()) {
            update.unset("message");
        } else {
            update.set("message", message.trim());
        }

        if (params != null && !params.isEmpty()) {
            update.set("params", new LinkedHashMap<>(params));
        }

        mongoTemplate.updateFirst(query, update, MODEL_RUN_COLLECTION);
    }

    public Document upsertPolicy(String policyId, Map<String, Object> fields) {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId is required.");
        }

        Query query = new Query(
                Criteria.where("policy_id").is(policyId)
        );

        Update update = new Update();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            update.set(entry.getKey(), entry.getValue());
        }
        Date now = new Date();
        update.set("updated_at", now);
        update.setOnInsert("reg_date", now);

        Document document = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                MODEL_POLICY_COLLECTION
        );

        if (document == null) {
            throw new IllegalStateException("Failed to upsert model-train policy.");
        }
        document.remove("_id");
        return document;
    }

    public List<Document> findAllPolicies() {
        Query query = new Query(
                Criteria.where("policy_id").exists(true)
                        .and("algo_code").exists(true)
                        .and(USE_FLAG_FIELD).is(ACTIVE_USE_FLAG)
        );
        query.with(Sort.by(Sort.Direction.ASC, "algo_code").and(Sort.by(Sort.Direction.ASC, "dataset_key")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, MODEL_POLICY_COLLECTION);
    }

    public List<Document> findPoliciesByDatasetKey(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return List.of();
        }

        Query query = new Query(
                Criteria.where("policy_id").exists(true)
                        .and("algo_code").exists(true)
                        .and("dataset_key").is(normalizedDatasetKey)
                        .and(USE_FLAG_FIELD).is(ACTIVE_USE_FLAG)
        );
        query.with(Sort.by(Sort.Direction.ASC, "algo_code").and(Sort.by(Sort.Direction.ASC, "policy_id")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, MODEL_POLICY_COLLECTION);
    }

    public List<Document> findEnabledPolicies() {
        Query query = new Query(
                Criteria.where("policy_id").exists(true)
                        .and("auto_train_enabled").is(true)
                        .and(USE_FLAG_FIELD).is(ACTIVE_USE_FLAG)
        );
        query.with(Sort.by(Sort.Direction.ASC, "updated_at"));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, MODEL_POLICY_COLLECTION);
    }

    public Document findPolicyByPolicyId(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return null;
        }

        Query query = new Query(
                Criteria.where("policy_id").is(policyId.trim())
        );
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_POLICY_COLLECTION);
    }

    public List<Document> findPoliciesByDatasetAndAlgo(String datasetKey, String algoCode) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        String normalizedAlgoCode = normalizeOptionalText(algoCode);
        if (normalizedDatasetKey == null || normalizedAlgoCode == null) {
            return List.of();
        }

        Query query = new Query(
                Criteria.where("dataset_key").is(normalizedDatasetKey)
                        .and("algo_code").is(normalizedAlgoCode)
                        .and(USE_FLAG_FIELD).is(ACTIVE_USE_FLAG)
        );
        query.with(Sort.by(Sort.Direction.ASC, "policy_id").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, MODEL_POLICY_COLLECTION);
    }

    public Document findDatasetConfigByDatasetKey(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return null;
        }

        Query query = new Query(
                Criteria.where("dataset_key").is(normalizedDatasetKey)
                        .and(USE_FLAG_FIELD).is(ACTIVE_USE_FLAG)
        );
        query.with(Sort.by(Sort.Direction.DESC, "updated_at"));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, DATASET_CONFIG_COLLECTION);
    }

    public Document findActiveFeaturePolicyByDatasetKey(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return null;
        }

        Query query = new Query(
                Criteria.where("dataset_key").is(normalizedDatasetKey)
                        .and("use_yn").is(ACTIVE_USE_YN)
        );
        query.with(Sort.by(Sort.Direction.DESC, "upd_date").and(Sort.by(Sort.Direction.DESC, "reg_date")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, FEATURE_POLICY_COLLECTION);
    }

    public List<Document> findActiveFeaturePolicies() {
        Query query = new Query(
                Criteria.where("dataset_key").exists(true).ne(null)
                        .and("use_yn").is(ACTIVE_USE_YN)
        );
        query.with(Sort.by(Sort.Direction.ASC, "dataset_key").and(Sort.by(Sort.Direction.DESC, "upd_date")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, FEATURE_POLICY_COLLECTION);
    }

    public Document findModelActiveByDatasetKey(String datasetKey) {
        return findLatestEnabledModelActiveByDatasetKey(datasetKey);
    }

    public Document findLatestEnabledModelActiveByDatasetKey(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return null;
        }

        Query query = new Query(
                Criteria.where("dataset_key").is(normalizedDatasetKey)
                        .and(USE_FLAG_FIELD).is(ACTIVE_USE_FLAG)
        );
        query.with(Sort.by(Sort.Direction.DESC, "updated_at").and(Sort.by(Sort.Direction.DESC, "reg_date")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_ACTIVE_COLLECTION);
    }

    public List<Document> findEnabledModelActives() {
        Query query = new Query(
                Criteria.where(USE_FLAG_FIELD).is(ACTIVE_USE_FLAG)
                        .and("dataset_key").exists(true).ne(null)
                        .and("active_policy_id").exists(true).ne(null)
                        .and("active_algo_name").exists(true).ne(null)
        );
        query.with(Sort.by(Sort.Direction.ASC, "dataset_key"));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, MODEL_ACTIVE_COLLECTION);
    }

    public Document upsertModelActive(String datasetKey, Map<String, Object> fields) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            throw new IllegalArgumentException("dataset_key is required.");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields is required.");
        }
        String activeAlgoName = normalizeOptionalText(fields == null ? null : fields.get("active_algo_name"));
        if (activeAlgoName == null) {
            throw new IllegalArgumentException("active_algo_name is required.");
        }

        Query query = new Query(Criteria.where("dataset_key").is(normalizedDatasetKey));
        Update update = new Update();
        update.set("dataset_key", normalizedDatasetKey);
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            update.set(entry.getKey(), entry.getValue());
        }

        Date now = new Date();
        update.set("updated_at", now);
        update.setOnInsert("reg_date", now);

        Document document = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                MODEL_ACTIVE_COLLECTION
        );
        if (document == null) {
            throw new IllegalStateException("Failed to upsert active model selection.");
        }
        document.remove("_id");
        return document;
    }

    public long countFeatureRowsByDatasetKey(String datasetKey) {
        Query query = new Query();
        applyFeatureDatasetCriteria(query, datasetKey);
        return mongoTemplate.count(query, FEATURE_COLLECTION);
    }

    public long countFeatureRowsByDatasetKeyAfterWindowEnd(String datasetKey, Object lastWindowEnd) {
        Query query = new Query();
        applyFeatureDatasetCriteria(query, datasetKey);
        if (lastWindowEnd != null) {
            query.addCriteria(Criteria.where("window_end").gt(lastWindowEnd));
        }
        return mongoTemplate.count(query, FEATURE_COLLECTION);
    }

    public List<Document> findLatestFeatureRowsByDatasetKey(String datasetKey, int limit) {
        Query query = new Query();
        applyFeatureDatasetCriteria(query, datasetKey);
        query.with(Sort.by(Sort.Direction.DESC, "window_end"));
        query.limit(Math.max(limit, 1));
        query.fields().exclude("_id");
        List<Document> latestRows = mongoTemplate.find(query, Document.class, FEATURE_COLLECTION);
        latestRows.sort(Comparator.comparing(document -> String.valueOf(document.get("window_start"))));
        return latestRows;
    }

    public List<Document> findFeatureRowsForDatasetDiscovery() {
        Query query = new Query();
        query.fields().exclude("_id");
        query.with(Sort.by(Sort.Direction.ASC, "window_start"));
        return mongoTemplate.find(query, Document.class, FEATURE_COLLECTION);
    }

    public List<Document> findFeatureRowsByDatasetKey(String datasetKey) {
        Query query = new Query();
        applyFeatureDatasetCriteria(query, datasetKey);
        query.with(Sort.by(Sort.Direction.ASC, "window_start"));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, FEATURE_COLLECTION);
    }

    public int countRawRowsInWindow(String datasetKey, Object windowStart, Object windowEnd) {
        if (windowStart == null || windowEnd == null) {
            return 0;
        }

        Query query = new Query();
        applyRawDatasetCriteria(query, datasetKey);
        query.addCriteria(Criteria.where(RAW_TIMESTAMP_FIELD).gte(windowStart).lte(windowEnd));
        return Math.toIntExact(mongoTemplate.count(query, RAW_COLLECTION));
    }

    public long countLabeledRowsByLabel(String datasetKey, int label) {
        Query query = new Query();
        applyLabeledRawDatasetCriteria(query, datasetKey);
        query.addCriteria(Criteria.where("label").is(label));
        return mongoTemplate.count(query, LABELED_RAW_COLLECTION);
    }

    public long countLabeledRowsByLabels(String datasetKey, List<Integer> labels) {
        if (labels == null || labels.isEmpty()) {
            return 0L;
        }
        Query query = new Query();
        applyLabeledRawDatasetCriteria(query, datasetKey);
        query.addCriteria(Criteria.where("label").in(labels));
        return mongoTemplate.count(query, LABELED_RAW_COLLECTION);
    }

    public List<Document> findLabeledRowsByLabels(String datasetKey, List<Integer> labels, int limit) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        Query query = new Query();
        applyLabeledRawDatasetCriteria(query, datasetKey);
        query.addCriteria(Criteria.where("label").in(labels));
        query.with(Sort.by(Sort.Direction.ASC, RAW_TIMESTAMP_FIELD).and(Sort.by(Sort.Direction.ASC, "_id")));
        query.limit(Math.max(limit, 1));
        return mongoTemplate.find(query, Document.class, LABELED_RAW_COLLECTION);
    }

    public int replaceAnomalyResultsByRunId(String runId, List<Document> anomalyRows) {
        ensureAnomalyResultCollectionReady();

        Query deleteQuery = new Query(Criteria.where("run_id").is(runId));
        mongoTemplate.remove(deleteQuery, ANOMALY_RESULT_COLLECTION);

        if (anomalyRows.isEmpty()) {
            return 0;
        }

        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ANOMALY_RESULT_COLLECTION);
        for (Document anomalyRow : anomalyRows) {
            bulkOperations.insert(anomalyRow);
        }

        BulkWriteResult writeResult = bulkOperations.execute();
        return writeResult.getInsertedCount();
    }

    public int replaceClassificationResultsByRunId(String runId, List<Document> classificationRows) {
        ensureClassificationResultCollectionReady();

        Query deleteQuery = new Query(Criteria.where("run_id").is(runId));
        mongoTemplate.remove(deleteQuery, CLASSIFICATION_RESULT_COLLECTION);

        if (classificationRows == null || classificationRows.isEmpty()) {
            return 0;
        }

        BulkOperations bulkOperations = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                CLASSIFICATION_RESULT_COLLECTION
        );
        for (Document row : classificationRows) {
            bulkOperations.insert(row);
        }
        BulkWriteResult writeResult = bulkOperations.execute();
        return writeResult.getInsertedCount();
    }

    public void replaceModelEvalByRunId(String runId, Document modelEvalDocument) {
        ensureModelEvalCollectionReady();

        Query deleteQuery = new Query(Criteria.where("run_id").is(runId));
        mongoTemplate.remove(deleteQuery, MODEL_EVAL_COLLECTION);

        if (modelEvalDocument == null) {
            return;
        }
        mongoTemplate.insert(modelEvalDocument, MODEL_EVAL_COLLECTION);
    }

    public List<Map<String, Object>> findAnomalyResultsByRunId(String runId, int limit) {
        return findAnomalyResultsByIdentity(runId, null, null, limit);
    }

    public List<Map<String, Object>> findAnomalyResultsByIdentity(
            String runId,
            String datasetKey,
            String equipmentId,
            int limit
    ) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return List.of();
        }

        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));

        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey != null) {
            query.addCriteria(Criteria.where("dataset_key").is(normalizedDatasetKey));
        }

        String normalizedEquipmentId = normalizeOptionalText(equipmentId);
        if (normalizedEquipmentId != null) {
            query.addCriteria(buildEquipmentCriteria(normalizedEquipmentId));
        }

        query.with(Sort.by(Sort.Direction.ASC, "window_start"));
        query.limit(limit);
        query.fields().exclude("_id");

        List<Document> documents = mongoTemplate.find(query, Document.class, ANOMALY_RESULT_COLLECTION);
        List<Map<String, Object>> rows = new ArrayList<>(documents.size());
        for (Document document : documents) {
            rows.add(normalizeDocument(document));
        }
        return rows;
    }

    private Criteria buildEquipmentCriteria(String equipmentId) {
        return new Criteria().orOperator(
                Criteria.where("equipment_id").is(equipmentId),
                Criteria.where(RAW_EQUIPMENT_FIELD).is(equipmentId)
        );
    }

    public List<Map<String, Object>> findAnomalyResultsByRunIdAfterWindowEnd(String runId, Date fromWindowEnd) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return List.of();
        }

        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        if (fromWindowEnd != null) {
            query.addCriteria(Criteria.where("window_end").gte(fromWindowEnd));
        }
        query.with(Sort.by(Sort.Direction.DESC, "window_end"));
        query.fields().exclude("_id");

        List<Document> documents = mongoTemplate.find(query, Document.class, ANOMALY_RESULT_COLLECTION);
        List<Map<String, Object>> rows = new ArrayList<>(documents.size());
        for (Document document : documents) {
            rows.add(normalizeDocument(document));
        }
        return rows;
    }

    public long countRunHistoryByPolicyId(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return 0L;
        }
        Query query = new Query(
                Criteria.where(DOC_TYPE_FIELD).is(DOC_TYPE_RUN)
                        .and("policy_id").is(policyId.trim())
        );
        return mongoTemplate.count(query, MODEL_RUN_COLLECTION);
    }

    public Document findLatestModelRunByPolicyId(String policyId) {
        String normalizedPolicyId = normalizeOptionalText(policyId);
        if (normalizedPolicyId == null) {
            return null;
        }
        Query query = new Query(
                Criteria.where("policy_id").is(normalizedPolicyId)
                        .andOperator(runDocTypeCriteria())
        );
        query.with(Sort.by(Sort.Direction.DESC, "reg_date"));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public Object findLatestAnomalyWindowEndByRunId(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return null;
        }
        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        query.with(Sort.by(Sort.Direction.DESC, "window_end"));
        query.limit(1);
        query.fields().include("window_end").exclude("_id");
        Document document = mongoTemplate.findOne(query, Document.class, ANOMALY_RESULT_COLLECTION);
        if (document == null) {
            return null;
        }
        return document.get("window_end");
    }

    public Document aggregateAnomalySummaryByRunId(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return null;
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("run_id").is(normalizedRunId)),
                Aggregation.group()
                        .count().as("total_count")
                        .sum(
                                ConditionalOperators.when(Criteria.where("is_anomaly").is(true))
                                        .then(1)
                                        .otherwise(0)
                        ).as("anomaly_count")
                        .avg("anomaly_score").as("avg_anomaly_score")
                        .avg("health_index").as("avg_health_index")
        );

        AggregationResults<Document> aggregationResults = mongoTemplate.aggregate(
                aggregation,
                ANOMALY_RESULT_COLLECTION,
                Document.class
        );
        Document summary = aggregationResults.getUniqueMappedResult();
        if (summary == null) {
            return null;
        }
        summary.remove("_id");
        return summary;
    }

    public Map<String, Long> aggregateAnomalyStatusCountsByRunId(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return Map.of();
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("run_id").is(normalizedRunId)),
                Aggregation.project()
                        .and(ConditionalOperators.ifNull("status").then("NO_DATA")).as("status"),
                Aggregation.group("status").count().as("count")
        );

        AggregationResults<Document> aggregationResults = mongoTemplate.aggregate(
                aggregation,
                ANOMALY_RESULT_COLLECTION,
                Document.class
        );

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Document row : aggregationResults.getMappedResults()) {
            String status = normalizeOptionalText(row.get("_id"));
            if (status == null) {
                status = "NO_DATA";
            }
            String normalizedStatus = status.toUpperCase(Locale.ROOT);
            long count = asLong(row.get("count"));
            statusCounts.merge(normalizedStatus, count, Long::sum);
        }
        return statusCounts;
    }

    public Document aggregateClassificationSummaryByRunId(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return null;
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("run_id").is(normalizedRunId)),
                Aggregation.group()
                        .count().as("total_count")
                        .sum(
                                ConditionalOperators.when(Criteria.where("prediction_label").is(1))
                                        .then(1)
                                        .otherwise(0)
                        ).as("anomaly_count")
                        .avg("prediction_probability_anomaly").as("avg_anomaly_score")
                        .avg("prediction_probability_normal").as("avg_health_index")
        );

        AggregationResults<Document> aggregationResults = mongoTemplate.aggregate(
                aggregation,
                CLASSIFICATION_RESULT_COLLECTION,
                Document.class
        );
        Document summary = aggregationResults.getUniqueMappedResult();
        if (summary == null) {
            return null;
        }
        summary.remove("_id");
        return summary;
    }

    public Map<String, Long> aggregateClassificationStatusCountsByRunId(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return Map.of();
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("run_id").is(normalizedRunId)),
                Aggregation.project()
                        .and(
                                ConditionalOperators.when(Criteria.where("prediction_label").is(1))
                                        .then(CRITICAL_STATUS)
                                        .otherwise(
                                                ConditionalOperators.when(Criteria.where("prediction_label").is(0))
                                                        .then(NORMAL_STATUS)
                                                        .otherwise(NO_DATA_STATUS)
                                        )
                        ).as("status"),
                Aggregation.group("status").count().as("count")
        );

        AggregationResults<Document> aggregationResults = mongoTemplate.aggregate(
                aggregation,
                CLASSIFICATION_RESULT_COLLECTION,
                Document.class
        );

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Document row : aggregationResults.getMappedResults()) {
            String status = normalizeOptionalText(row.get("_id"));
            if (status == null) {
                status = NO_DATA_STATUS;
            }
            String normalizedStatus = status.toUpperCase(Locale.ROOT);
            long count = asLong(row.get("count"));
            statusCounts.merge(normalizedStatus, count, Long::sum);
        }
        return statusCounts;
    }

    public Document findLatestModelEvalByRunId(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return null;
        }

        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        query.with(Sort.by(Sort.Direction.DESC, "reg_date"));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_EVAL_COLLECTION);
    }

    public long countClassificationResultsByRunId(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return 0L;
        }
        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        return mongoTemplate.count(query, CLASSIFICATION_RESULT_COLLECTION);
    }

    public long countClassificationByCorrectYn(String runId, String correctYn) {
        String normalizedRunId = normalizeOptionalText(runId);
        String normalizedCorrectYn = normalizeOptionalText(correctYn);
        if (normalizedRunId == null || normalizedCorrectYn == null) {
            return 0L;
        }
        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        query.addCriteria(Criteria.where("correct_yn").regex("^" + normalizedCorrectYn + "$", "i"));
        return mongoTemplate.count(query, CLASSIFICATION_RESULT_COLLECTION);
    }

    public Map<String, Long> aggregateClassificationErrorTypeCountsByRunId(String runId) {
        String normalizedRunId = normalizeOptionalText(runId);
        if (normalizedRunId == null) {
            return Map.of();
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("run_id").is(normalizedRunId)),
                Aggregation.project()
                        .and(
                                ConditionalOperators.ifNull("error_type").then("UNKNOWN")
                        ).as("error_type"),
                Aggregation.group("error_type").count().as("count")
        );

        AggregationResults<Document> aggregationResults = mongoTemplate.aggregate(
                aggregation,
                CLASSIFICATION_RESULT_COLLECTION,
                Document.class
        );

        Map<String, Long> countsByErrorType = new LinkedHashMap<>();
        for (Document row : aggregationResults.getMappedResults()) {
            String errorType = normalizeOptionalText(row.get("_id"));
            if (errorType == null) {
                continue;
            }
            countsByErrorType.put(errorType.toUpperCase(Locale.ROOT), asLong(row.get("count")));
        }
        return countsByErrorType;
    }

    public long countLabeledRowsByDatasetKey(String datasetKey) {
        Query query = new Query();
        applyLabeledRawDatasetCriteria(query, datasetKey);
        return mongoTemplate.count(query, LABELED_RAW_COLLECTION);
    }

    private void applyFeatureDatasetCriteria(Query query, String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return;
        }

        List<Criteria> criteriaParts = new ArrayList<>();
        criteriaParts.add(Criteria.where(FEATURE_DATASET_KEY_PATH).is(normalizedDatasetKey));
        criteriaParts.add(Criteria.where(FEATURE_META_DATASET_KEY_PATH).is(normalizedDatasetKey));

        String datasetKeyHash = schemaResolver.datasetKeyHash(normalizedDatasetKey);
        if (!datasetKeyHash.isBlank()) {
            criteriaParts.add(Criteria.where(FEATURE_DATASET_KEY_HASH_PATH).is(datasetKeyHash));
        }

        query.addCriteria(new Criteria().orOperator(criteriaParts.toArray(Criteria[]::new)));
    }

    private void applyRawDatasetCriteria(Query query, String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return;
        }

        String equipmentScope = schemaResolver.resolveEquipmentScopeFromDatasetKey(normalizedDatasetKey);
        if (equipmentScope == null || equipmentScope.isBlank()) {
            return;
        }
        if ("all".equalsIgnoreCase(equipmentScope) || "default".equalsIgnoreCase(equipmentScope)) {
            return;
        }
        query.addCriteria(new Criteria().orOperator(
                Criteria.where(RAW_EQUIPMENT_FIELD).is(equipmentScope),
                Criteria.where("equipment_id").is(equipmentScope)
        ));
    }

    private void applyLabeledRawDatasetCriteria(Query query, String datasetKey) {
        applyRawDatasetCriteria(query, datasetKey);
    }

    private Map<String, Object> normalizeDocument(Document document) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            normalized.put(entry.getKey(), schemaResolver.normalizeResponseValue(entry.getValue()));
        }
        return normalized;
    }

    private String normalizeOptionalText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
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

    private Criteria runDocTypeCriteria() {
        return new Criteria().orOperator(
                Criteria.where(DOC_TYPE_FIELD).is(DOC_TYPE_RUN),
                Criteria.where(DOC_TYPE_FIELD).exists(false)
        );
    }

    private void ensureAnomalyResultCollectionReady() {
        if (anomalyCollectionPrepared.get()) {
            return;
        }
        synchronized (anomalyCollectionPrepared) {
            if (anomalyCollectionPrepared.get()) {
                return;
            }
            ensureAnomalyResultCollectionValidator();
            ensureAnomalyResultIndexes();
            anomalyCollectionPrepared.set(true);
        }
    }

    private void ensureAnomalyResultCollectionValidator() {
        Document validator = new Document("$jsonSchema", buildAnomalyResultJsonSchema());
        if (!mongoTemplate.collectionExists(ANOMALY_RESULT_COLLECTION)) {
            Document createCommand = new Document("create", ANOMALY_RESULT_COLLECTION)
                    .append("validator", validator)
                    .append("validationLevel", "moderate")
                    .append("validationAction", "error");
            try {
                mongoTemplate.executeCommand(createCommand);
                return;
            } catch (MongoCommandException commandException) {
                if (commandException.getErrorCode() != 48) {
                    throw commandException;
                }
            }
        }

        Document collModCommand = new Document("collMod", ANOMALY_RESULT_COLLECTION)
                .append("validator", validator)
                .append("validationLevel", "moderate")
                .append("validationAction", "error");
        mongoTemplate.executeCommand(collModCommand);
    }

    private void ensureAnomalyResultIndexes() {
        List<IndexInfo> indexInfos = mongoTemplate.indexOps(ANOMALY_RESULT_COLLECTION).getIndexInfo();
        for (IndexInfo indexInfo : indexInfos) {
            if (LEGACY_ANOMALY_INDEX.equals(indexInfo.getName())) {
                mongoTemplate.indexOps(ANOMALY_RESULT_COLLECTION).dropIndex(LEGACY_ANOMALY_INDEX);
                break;
            }
        }

        mongoTemplate.indexOps(ANOMALY_RESULT_COLLECTION).ensureIndex(
                new Index()
                        .on("dataset_key", Sort.Direction.ASC)
                        .on("equipment_id", Sort.Direction.ASC)
                        .on("window_start", Sort.Direction.ASC)
                        .on("window_end", Sort.Direction.ASC)
                        .named(ANOMALY_INDEX_DATASET_EQUIPMENT_WINDOW)
        );
        mongoTemplate.indexOps(ANOMALY_RESULT_COLLECTION).ensureIndex(
                new Index()
                        .on("run_id", Sort.Direction.ASC)
                        .named(ANOMALY_INDEX_RUN_ID)
        );
        mongoTemplate.indexOps(ANOMALY_RESULT_COLLECTION).ensureIndex(
                new Index()
                        .on("cause_generated", Sort.Direction.ASC)
                        .on("dataset_key", Sort.Direction.ASC)
                        .on("equipment_id", Sort.Direction.ASC)
                        .on("reg_date", Sort.Direction.DESC)
                        .named(ANOMALY_INDEX_CAUSE_GENERATED)
        );
        mongoTemplate.indexOps(ANOMALY_RESULT_COLLECTION).ensureIndex(
                new Index()
                        .on("alert_generated", Sort.Direction.ASC)
                        .on("dataset_key", Sort.Direction.ASC)
                        .on("equipment_id", Sort.Direction.ASC)
                        .on("reg_date", Sort.Direction.DESC)
                        .named(ANOMALY_INDEX_ALERT_GENERATED)
        );
    }

    private Document buildAnomalyResultJsonSchema() {
        return new Document("bsonType", "object")
                .append("required", List.of(
                        "run_id",
                        "dataset_key",
                        "equipment_id",
                        "window_start",
                        "window_end",
                        "input_features",
                        "anomaly_score",
                        "is_anomaly",
                        "health_index",
                        "status",
                        "reg_date"
                ))
                .append("properties", new Document()
                        .append("run_id", requiredNonBlankStringSchema())
                        .append("dataset_key", requiredNonBlankStringSchema())
                        .append("MCCODE", optionalStringSchema())
                        .append("equipment_id", requiredNonBlankStringSchema())
                        .append("sensor_id", optionalStringSchema())
                        .append("lot_no", optionalStringSchema())
                        .append("window_start", new Document("bsonType", "date"))
                        .append("window_end", new Document("bsonType", "date"))
                        .append("input_features", new Document("bsonType", "object"))
                        .append("anomaly_score", numericSchema())
                        .append("is_anomaly", new Document("bsonType", "bool"))
                        .append("health_index", numericSchema())
                        .append("status", requiredNonBlankStringSchema())
                        .append("cause_generated", new Document("bsonType", "bool"))
                        .append("alert_generated", new Document("bsonType", "bool"))
                        .append("reg_date", new Document("bsonType", "date"))
                        .append("updated_at", new Document("bsonType", "date"))
                );
    }

    private void ensureClassificationResultCollectionReady() {
        if (classificationCollectionPrepared.get()) {
            return;
        }
        synchronized (classificationCollectionPrepared) {
            if (classificationCollectionPrepared.get()) {
                return;
            }
            ensureClassificationResultCollectionValidator();
            ensureClassificationResultIndexes();
            classificationCollectionPrepared.set(true);
        }
    }

    private void ensureClassificationResultCollectionValidator() {
        Document validator = new Document("$jsonSchema", buildClassificationResultJsonSchema());
        if (!mongoTemplate.collectionExists(CLASSIFICATION_RESULT_COLLECTION)) {
            Document createCommand = new Document("create", CLASSIFICATION_RESULT_COLLECTION)
                    .append("validator", validator)
                    .append("validationLevel", "moderate")
                    .append("validationAction", "error");
            try {
                mongoTemplate.executeCommand(createCommand);
                return;
            } catch (MongoCommandException commandException) {
                if (commandException.getErrorCode() != 48) {
                    throw commandException;
                }
            }
        }

        Document collModCommand = new Document("collMod", CLASSIFICATION_RESULT_COLLECTION)
                .append("validator", validator)
                .append("validationLevel", "moderate")
                .append("validationAction", "error");
        mongoTemplate.executeCommand(collModCommand);
    }

    private void ensureClassificationResultIndexes() {
        mongoTemplate.indexOps(CLASSIFICATION_RESULT_COLLECTION).ensureIndex(
                new Index()
                        .on("run_id", Sort.Direction.ASC)
                        .on("dataset_key", Sort.Direction.ASC)
                        .on("source_id", Sort.Direction.ASC)
                        .on("split_type", Sort.Direction.ASC)
                        .named(CLASSIFICATION_INDEX_RUN_DATASET_SOURCE_SPLIT)
        );
    }

    private Document buildClassificationResultJsonSchema() {
        return new Document("bsonType", "object")
                .append("required", List.of(
                        "run_id",
                        "dataset_key",
                        "algo_code",
                        "labeled_doc_id",
                        "actual_label",
                        "prediction_label",
                        "split_type",
                        "reg_date"
                ))
                .append("properties", new Document()
                        .append("run_id", requiredNonBlankStringSchema())
                        .append("dataset_key", requiredNonBlankStringSchema())
                        .append("policy_id", optionalStringSchema())
                        .append("algo_code", requiredNonBlankStringSchema())
                        .append("algo_name", optionalStringSchema())
                        .append("labeled_doc_id", requiredNonBlankStringSchema())
                        .append("source_id", optionalStringSchema())
                        .append("actual_label", integerSchema())
                        .append("prediction_label", integerSchema())
                        .append("prediction_probability", numericSchema())
                        .append("prediction_probability_normal", numericSchema())
                        .append("prediction_probability_anomaly", numericSchema())
                        .append("split_type", requiredNonBlankStringSchema())
                        .append("input_features", new Document("bsonType", "object"))
                        .append("correct_yn", optionalStringSchema())
                        .append("error_type", optionalStringSchema())
                        .append("reg_date", new Document("bsonType", "date"))
                );
    }

    private void ensureModelEvalCollectionReady() {
        if (modelEvalCollectionPrepared.get()) {
            return;
        }
        synchronized (modelEvalCollectionPrepared) {
            if (modelEvalCollectionPrepared.get()) {
                return;
            }
            ensureModelEvalCollectionValidator();
            ensureModelEvalIndexes();
            modelEvalCollectionPrepared.set(true);
        }
    }

    private void ensureModelEvalCollectionValidator() {
        Document validator = new Document("$jsonSchema", buildModelEvalJsonSchema());
        if (!mongoTemplate.collectionExists(MODEL_EVAL_COLLECTION)) {
            Document createCommand = new Document("create", MODEL_EVAL_COLLECTION)
                    .append("validator", validator)
                    .append("validationLevel", "moderate")
                    .append("validationAction", "error");
            try {
                mongoTemplate.executeCommand(createCommand);
                return;
            } catch (MongoCommandException commandException) {
                if (commandException.getErrorCode() != 48) {
                    throw commandException;
                }
            }
        }

        Document collModCommand = new Document("collMod", MODEL_EVAL_COLLECTION)
                .append("validator", validator)
                .append("validationLevel", "moderate")
                .append("validationAction", "error");
        mongoTemplate.executeCommand(collModCommand);
    }

    private void ensureModelEvalIndexes() {
        mongoTemplate.indexOps(MODEL_EVAL_COLLECTION).ensureIndex(
                new Index()
                        .on("run_id", Sort.Direction.ASC)
                        .on("dataset_key", Sort.Direction.ASC)
                        .on("algo_code", Sort.Direction.ASC)
                        .on("reg_date", Sort.Direction.DESC)
                        .named(MODELEVAL_INDEX_RUN_DATASET_ALGO_REGDATE)
        );
    }

    private Document buildModelEvalJsonSchema() {
        return new Document("bsonType", "object")
                .append("required", List.of(
                        "run_id",
                        "dataset_key",
                        "algo_code",
                        "accuracy",
                        "precision",
                        "recall",
                        "f1_score",
                        "tp",
                        "tn",
                        "fp",
                        "fn",
                        "test_count",
                        "reg_date"
                ))
                .append("properties", new Document()
                        .append("run_id", requiredNonBlankStringSchema())
                        .append("dataset_key", requiredNonBlankStringSchema())
                        .append("policy_id", optionalStringSchema())
                        .append("algo_code", requiredNonBlankStringSchema())
                        .append("algo_name", optionalStringSchema())
                        .append("label_version", optionalStringSchema())
                        .append("total_count", integerSchema())
                        .append("train_count", integerSchema())
                        .append("test_count", integerSchema())
                        .append("validation_count", integerSchema())
                        .append("excluded_unknown_count", integerSchema())
                        .append("normal_count", integerSchema())
                        .append("anomaly_count", integerSchema())
                        .append("accuracy", numericSchema())
                        .append("precision", numericSchema())
                        .append("recall", numericSchema())
                        .append("f1_score", numericSchema())
                        .append("tp", integerSchema())
                        .append("tn", integerSchema())
                        .append("fp", integerSchema())
                        .append("fn", integerSchema())
                        .append("train_valid_ratio", optionalStringSchema())
                        .append("class_weight", optionalStringSchema())
                        .append("random_state", integerSchema())
                        .append("params", new Document("bsonType", "object"))
                        .append("feature_columns", new Document("bsonType", "array"))
                        .append("excluded_columns", new Document("bsonType", "array"))
                        .append("feature_importances", new Document("bsonType", "array"))
                        .append("evaluation_method", optionalStringSchema())
                        .append("message", optionalStringSchema())
                        .append("reg_date", new Document("bsonType", "date"))
                        .append("updated_at", new Document("bsonType", "date"))
                );
    }

    private Document requiredNonBlankStringSchema() {
        return new Document("bsonType", "string").append("minLength", 1);
    }

    private Document optionalStringSchema() {
        return new Document("bsonType", "string");
    }

    private Document numericSchema() {
        return new Document("bsonType", List.of("double", "int", "long", "decimal"));
    }

    private Document integerSchema() {
        return new Document("bsonType", List.of("int", "long", "decimal", "double"));
    }
}
