package com.demo.insight.anomalycause;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.demo.insight.common.schema.DynamicSchemaResolver;

@Repository
public class AnomalyCauseRepository {

    private static final String MODEL_RUN_COLLECTION = "thismodelrun";
    private static final String ANOMALY_RESULT_COLLECTION = "thisanomalyresult";
    private static final String ANOMALY_CAUSE_COLLECTION = "thisanomalycause";
    private static final String FEATURE_CAUSE_MAP_COLLECTION = "tmst_feature_cause_map";
    private static final String FEATURE_COLLECTION = "thisfeature";
    private static final String FEATURE_DATASET_KEY_PATH = "dataset_key";
    private static final String FEATURE_META_DATASET_KEY_PATH = "feature_values.META.dataset_key";
    private static final String FEATURE_META_DATASET_KEY_HASH_PATH = "feature_values.META.dataset_key_hash";
    private static final int FALLBACK_NORMAL_SAMPLE_LIMIT = 10_000;

    private static final String DOC_TYPE_FIELD = "doc_type";
    private static final String DOC_TYPE_RUN = "RUN";

    private final MongoTemplate mongoTemplate;
    private final DynamicSchemaResolver schemaResolver;

    public AnomalyCauseRepository(
            MongoTemplate mongoTemplate,
            DynamicSchemaResolver schemaResolver
    ) {
        this.mongoTemplate = mongoTemplate;
        this.schemaResolver = schemaResolver;
    }

    public List<Document> findRecentRunsWithAnomalyResults(int limit) {
        int safeLimit = Math.max(limit, 1);
        List<Document> pipeline = new ArrayList<>();

        pipeline.add(
                new Document("$match", new Document("run_id", new Document("$exists", true).append("$ne", null))
                        .append("dataset_key", new Document("$exists", true).append("$ne", null)))
        );
        pipeline.add(
                new Document("$group", new Document("_id", new Document("run_id", "$run_id").append("dataset_key", "$dataset_key"))
                        .append("executed_at", new Document("$max", new Document("$ifNull", List.of("$reg_date", "$window_end"))))
                        .append("equipment_id", new Document("$first", new Document("$ifNull", List.of("$equipment_id", "$MCCODE"))))
                        .append("MCCODE", new Document("$first", new Document("$ifNull", List.of("$MCCODE", "$equipment_id")))))
        );

        pipeline.add(
                new Document("$lookup", new Document("from", MODEL_RUN_COLLECTION)
                        .append("let", new Document("runId", "$_id.run_id"))
                        .append("pipeline", List.of(
                                new Document("$match", new Document("$expr", new Document("$eq", List.of("$run_id", "$$runId")))),
                                new Document("$sort", new Document("reg_date", -1)),
                                new Document("$limit", 1),
                                new Document("$project", new Document("_id", 0)
                                        .append("algo_code", 1)
                                        .append("algo_name", 1)
                                        .append("dataset_key", 1)
                                        .append("reg_date", 1))
                        ))
                        .append("as", "run_meta"))
        );

        pipeline.add(new Document("$set", new Document("run_meta", new Document("$first", "$run_meta"))));
        pipeline.add(
                new Document("$set", new Document("executed_at", new Document("$ifNull", List.of("$run_meta.reg_date", "$executed_at")))
                        .append("algo_code", "$run_meta.algo_code")
                        .append("algo_name", "$run_meta.algo_name")
                        .append("dataset_key", new Document("$ifNull", List.of("$run_meta.dataset_key", "$_id.dataset_key"))))
        );
        pipeline.add(new Document("$sort", new Document("executed_at", -1).append("_id.run_id", -1)));
        pipeline.add(new Document("$limit", safeLimit));
        pipeline.add(
                new Document("$project", new Document("_id", 0)
                        .append("run_id", "$_id.run_id")
                        .append("dataset_key", "$dataset_key")
                        .append("equipment_id", "$equipment_id")
                        .append("MCCODE", "$MCCODE")
                        .append("algo_code", "$algo_code")
                        .append("algo_name", "$algo_name")
                        .append("executed_at", "$executed_at"))
        );

        List<Document> results = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(ANOMALY_RESULT_COLLECTION)
                .aggregate(pipeline)
                .allowDiskUse(true)) {
            row.remove("_id");
            results.add(row);
        }
        return results;
    }

    public long countAnomalyWindows(WindowQuery windowQuery) {
        Query query = new Query(buildWindowMatchCriteria(windowQuery));
        return mongoTemplate.count(query, ANOMALY_RESULT_COLLECTION);
    }

    public List<Document> findAnomalyWindows(WindowQuery windowQuery, int skip, int limit) {
        int safeLimit = Math.max(limit, 1);
        int safeSkip = Math.max(skip, 0);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", buildWindowMatchCriteria(windowQuery).getCriteriaObject()));
        pipeline.add(new Document("$sort", new Document("window_start", -1)));
        pipeline.add(
                new Document("$lookup", new Document("from", ANOMALY_CAUSE_COLLECTION)
                        .append("let", new Document("runId", "$run_id")
                                .append("datasetKey", "$dataset_key")
                                .append("equipmentId", new Document("$ifNull", List.of("$equipment_id", "$MCCODE")))
                                .append("windowStart", "$window_start")
                                .append("windowEnd", "$window_end"))
                        .append("pipeline", List.of(
                                new Document("$match", new Document("$expr", new Document("$and", List.of(
                                        new Document("$eq", List.of("$run_id", "$$runId")),
                                        new Document("$eq", List.of("$dataset_key", "$$datasetKey")),
                                        new Document("$eq", List.of(new Document("$ifNull", List.of("$equipment_id", "$MCCODE")), "$$equipmentId")),
                                        new Document("$eq", List.of("$window_start", "$$windowStart")),
                                        new Document("$eq", List.of("$window_end", "$$windowEnd"))
                                )))),
                                new Document("$sort", new Document("updated_at", -1).append("created_at", -1)),
                                new Document("$limit", 1),
                                new Document("$project", new Document("_id", 0)
                                        .append("cause_summary", 1)
                                        .append("cause_candidates", 1)
                                        .append("group_scores", 1))
                        ))
                        .append("as", "cause_docs"))
        );
        pipeline.add(new Document("$set", new Document("cause_generated", new Document("$gt", List.of(new Document("$size", "$cause_docs"), 0)))));
        pipeline.add(new Document("$set", new Document("cause_doc", new Document("$first", "$cause_docs"))));
        pipeline.add(new Document("$skip", safeSkip));
        pipeline.add(new Document("$limit", safeLimit));
        pipeline.add(
                new Document("$project", new Document("_id", 0)
                        .append("run_id", 1)
                        .append("dataset_key", 1)
                        .append("equipment_id", 1)
                        .append("MCCODE", 1)
                        .append("window_start", 1)
                        .append("window_end", 1)
                        .append("anomaly_score", 1)
                        .append("health_index", 1)
                        .append("status", 1)
                        .append("cause_generated", 1)
                        .append("cause_summary", new Document("$ifNull", List.of("$cause_doc.cause_summary", List.of())))
                        .append("cause_candidates", new Document("$ifNull", List.of("$cause_doc.cause_candidates", List.of())))
                        .append("group_scores", new Document("$ifNull", List.of("$cause_doc.group_scores", List.of()))))
        );

        List<Document> results = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(ANOMALY_RESULT_COLLECTION)
                .aggregate(pipeline)
                .allowDiskUse(true)) {
            results.add(row);
        }
        return results;
    }

    public Document findAnomalyResultWindow(String runId, String datasetKey, Date windowStart, Date windowEnd) {
        return findAnomalyResultWindow(runId, datasetKey, windowStart, windowEnd, null);
    }

    public Document findAnomalyCauseWindow(String runId, String datasetKey, Date windowStart, Date windowEnd) {
        return findAnomalyCauseWindow(runId, datasetKey, windowStart, windowEnd, null);
    }

    public Document findAnomalyResultWindow(
            String runId,
            String datasetKey,
            Date windowStart,
            Date windowEnd,
            String equipmentId
    ) {
        Query query = new Query(buildWindowIdentityCriteria(runId, datasetKey, windowStart, windowEnd, equipmentId));
        return mongoTemplate.findOne(query, Document.class, ANOMALY_RESULT_COLLECTION);
    }

    public Document findAnomalyCauseWindow(
            String runId,
            String datasetKey,
            Date windowStart,
            Date windowEnd,
            String equipmentId
    ) {
        Query query = new Query(buildWindowIdentityCriteria(runId, datasetKey, windowStart, windowEnd, equipmentId));
        query.with(Sort.by(Sort.Direction.DESC, "updated_at").and(Sort.by(Sort.Direction.DESC, "created_at")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, ANOMALY_CAUSE_COLLECTION);
    }

    public List<Document> findFeatureCauseMaps(String datasetKey, List<String> sourceFields) {
        if (sourceFields == null || sourceFields.isEmpty()) {
            return List.of();
        }

        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);

        List<Criteria> datasetCriteria = new ArrayList<>();
        if (normalizedDatasetKey != null) {
            datasetCriteria.add(Criteria.where("dataset_key").is(normalizedDatasetKey));
        }
        datasetCriteria.add(Criteria.where("dataset_key").exists(false));
        datasetCriteria.add(Criteria.where("dataset_key").is(null));
        datasetCriteria.add(Criteria.where("dataset_key").is(""));

        Query query = new Query(
                Criteria.where("source_field").in(sourceFields)
                        .and("useflag").regex("^y$", "i")
                        .andOperator(new Criteria().orOperator(datasetCriteria.toArray(Criteria[]::new)))
        );
        query.with(Sort.by(Sort.Direction.ASC, "sortno").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, FEATURE_CAUSE_MAP_COLLECTION);
    }

    public List<Document> findAnomalyResultsByRunAndEquipment(
            String runId,
            String datasetKey,
            String equipmentId
    ) {
        Query query = new Query(buildRunDatasetEquipmentCriteria(runId, datasetKey, equipmentId));
        query.with(Sort.by(Sort.Direction.ASC, "window_start"));
        return mongoTemplate.find(query, Document.class, ANOMALY_RESULT_COLLECTION);
    }

    public boolean existsAnomalyRunIdentity(String runId, String datasetKey, String equipmentId) {
        Query query = new Query(buildRunDatasetEquipmentCriteria(runId, datasetKey, equipmentId));
        query.limit(1);
        return mongoTemplate.exists(query, ANOMALY_RESULT_COLLECTION);
    }

    // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    //  [癰궰野? findMissingCauseTargets: $lookup ?혵椰??혪 cause_generated ?혣?혱域?疫꿸퀡?
    //  ?紐꺜띻퉮혡??혙?혬: { cause_generated: 1, reg_date: -1, window_start: -1 }
    // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????

    public List<Document> findMissingCauseTargets(int limit) {
        return findMissingCauseTargets(null, limit);
    }

    public List<Document> findMissingCauseTargets(String datasetKey, int limit) {
        int safeLimit = Math.max(limit, 1);
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);

        Document matchDoc = new Document()
                .append("run_id", new Document("$exists", true).append("$ne", null))
                .append("window_start", new Document("$exists", true).append("$ne", null))
                .append("window_end", new Document("$exists", true).append("$ne", null))
                .append("cause_generated", new Document("$ne", true));

        if (normalizedDatasetKey != null) {
            matchDoc.append("dataset_key", normalizedDatasetKey);
        } else {
            matchDoc.append("dataset_key", new Document("$exists", true).append("$ne", null));
        }

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", matchDoc));
        pipeline.add(new Document("$sort", new Document("reg_date", -1).append("window_start", -1)));
        pipeline.add(new Document("$limit", safeLimit));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("run_id", 1)
                .append("dataset_key", 1)
                .append("equipment_id", 1)
                .append("MCCODE", 1)
                .append("window_start", 1)
                .append("window_end", 1)
                .append("anomaly_score", 1)
                .append("health_index", 1)
                .append("status", 1)
                .append("input_features", 1)));

        List<Document> results = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(ANOMALY_RESULT_COLLECTION)
                .aggregate(pipeline)
                .maxTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .allowDiskUse(true)) {
            results.add(row);
        }
        return results;
    }

    public List<Document> findNormalAnomalyResultsByRunAndDataset(
            String runId,
            String datasetKey,
            String equipmentId
    ) {
        Query query = new Query(
                buildRunDatasetEquipmentCriteria(runId, datasetKey, equipmentId)
                        .and("status").regex("^NORMAL$", "i")
        );
        query.with(Sort.by(Sort.Direction.ASC, "window_start"));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, ANOMALY_RESULT_COLLECTION);
    }

    public List<Document> findNormalAnomalyResultsByDataset(String datasetKey, String equipmentId) {
        Query query = new Query(
                buildDatasetEquipmentCriteria(datasetKey, equipmentId)
                        .and("status").regex("^NORMAL$", "i")
        );
        query.with(Sort.by(Sort.Direction.DESC, "window_end"));
        query.limit(FALLBACK_NORMAL_SAMPLE_LIMIT);
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, ANOMALY_RESULT_COLLECTION);
    }

    public Document findFeatureWindow(String datasetKey, Date windowStart, Date windowEnd) {
        Query query = new Query(
                Criteria.where("window_start").is(windowStart)
                        .and("window_end").is(windowEnd)
                        .andOperator(buildFeatureDatasetCriteria(datasetKey))
        );
        query.with(Sort.by(Sort.Direction.DESC, "updated_at").and(Sort.by(Sort.Direction.DESC, "reg_date")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, FEATURE_COLLECTION);
    }

    // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    //  [癰궰野? upsertAnomalyCause: cause ?????혙 thisanomalyresult?혨
    //         cause_generated = true ?혣?혱域??紐끒뙿?
    // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????

    public Document upsertAnomalyCause(Document causeDocument) {
        String runId = normalizeRequiredText(causeDocument.get("run_id"), "run_id");
        String datasetKey = normalizeRequiredText(causeDocument.get("dataset_key"), "dataset_key");
        Object windowStart = causeDocument.get("window_start");
        Object windowEnd = causeDocument.get("window_end");
        String equipmentId = firstNonBlank(
                normalizeOptionalText(causeDocument.get("equipment_id")),
                normalizeOptionalText(causeDocument.get("MCCODE"))
        );
        if (!(windowStart instanceof Date) || !(windowEnd instanceof Date)) {
            throw new IllegalArgumentException("window_start and window_end must be Date.");
        }

        Query query = new Query(
                buildWindowIdentityCriteria(
                        runId,
                        datasetKey,
                        (Date) windowStart,
                        (Date) windowEnd,
                        equipmentId
                )
        );

        Update update = new Update();
        Date now = (Date) causeDocument.getOrDefault("updated_at", new Date());
        for (Map.Entry<String, Object> entry : causeDocument.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if ("created_at".equals(key)) {
                continue;
            }
            update.set(key, entry.getValue());
        }
        update.set("updated_at", now);
        update.setOnInsert("created_at", causeDocument.getOrDefault("created_at", now));

        Document updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                ANOMALY_CAUSE_COLLECTION
        );

        if (updated == null) {
            throw new IllegalStateException("Failed to upsert anomaly cause document.");
        }

        // ?혚 cause ?????源껊궗 ?혵 anomalyresult?혱 cause_generated ?혣?혱域밸챶? true嚥≤?揶쏄퉮혢田?
        markCauseGenerated(runId, datasetKey, (Date) windowStart, (Date) windowEnd, equipmentId);

        updated.remove("_id");
        return updated;
    }

    /**
     * thisanomalyresult ?얜챷혙혵?혨 cause_generated = true ?혣?혱域밸챶? ?紐끒뙿?혵??
     * findMissingCauseTargets ?혨?혵 $lookup ?혛????＆?쒕떯? 沃섎챷???椰꾨똻혶혙 鈺곌퀬혳혣?혱疫??혙?혵 ??맞뤒?
     */
    private void markCauseGenerated(String runId, String datasetKey, Date windowStart, Date windowEnd, String equipmentId) {
        Query resultQuery = new Query(buildWindowIdentityCriteria(runId, datasetKey, windowStart, windowEnd, equipmentId));
        mongoTemplate.updateFirst(
                resultQuery,
                new Update()
                        .set("cause_generated", true)
                        .set("updated_at", new Date()),
                ANOMALY_RESULT_COLLECTION
        );
    }

    private Criteria buildWindowMatchCriteria(WindowQuery windowQuery) {
        Criteria criteria = buildRunDatasetEquipmentCriteria(
                windowQuery.runId(),
                windowQuery.datasetKey(),
                windowQuery.equipmentId()
        );

        if (windowQuery.status() != null) {
            criteria.and("status").is(windowQuery.status());
        }
        if (windowQuery.from() != null) {
            criteria.and("window_start").gte(windowQuery.from());
        }
        if (windowQuery.to() != null) {
            criteria.and("window_end").lte(windowQuery.to());
        }
        return criteria;
    }

    private Document buildMissingCauseTargetMatch(String datasetKey) {
        Document match = new Document("run_id", new Document("$exists", true).append("$ne", null))
                .append("dataset_key", new Document("$exists", true).append("$ne", null))
                .append("window_start", new Document("$exists", true).append("$ne", null))
                .append("window_end", new Document("$exists", true).append("$ne", null));
        if (datasetKey != null) {
            match.put("dataset_key", datasetKey);
        }
        return match;
    }

    public boolean existsModelRun(String runId) {
        Query query = new Query(
                Criteria.where("run_id").is(runId)
                        .andOperator(runDocTypeCriteria())
        );
        return mongoTemplate.exists(query, MODEL_RUN_COLLECTION);
    }

    private Criteria runDocTypeCriteria() {
        return new Criteria().orOperator(
                Criteria.where(DOC_TYPE_FIELD).is(DOC_TYPE_RUN),
                Criteria.where(DOC_TYPE_FIELD).exists(false)
        );
    }

    private Criteria buildFeatureDatasetCriteria(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalizedDatasetKey == null) {
            return new Criteria();
        }

        String datasetKeyHash = schemaResolver.datasetKeyHash(normalizedDatasetKey);
        List<Criteria> criteriaParts = new ArrayList<>();
        criteriaParts.add(Criteria.where(FEATURE_DATASET_KEY_PATH).is(normalizedDatasetKey));
        criteriaParts.add(Criteria.where(FEATURE_META_DATASET_KEY_PATH).is(normalizedDatasetKey));
        if (!datasetKeyHash.isBlank()) {
            criteriaParts.add(Criteria.where(FEATURE_META_DATASET_KEY_HASH_PATH).is(datasetKeyHash));
        }
        return new Criteria().orOperator(criteriaParts.toArray(Criteria[]::new));
    }

    private String normalizeRequiredText(Object value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeOptionalText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    private Criteria buildWindowIdentityCriteria(
            String runId,
            String datasetKey,
            Date windowStart,
            Date windowEnd,
            String equipmentId
    ) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("run_id").is(runId));
        criteriaList.add(Criteria.where("dataset_key").is(datasetKey));
        criteriaList.add(Criteria.where("window_start").is(windowStart));
        criteriaList.add(Criteria.where("window_end").is(windowEnd));

        String normalizedEquipmentId = normalizeOptionalText(equipmentId);
        if (normalizedEquipmentId != null) {
            criteriaList.add(
                    new Criteria().orOperator(
                            Criteria.where("equipment_id").is(normalizedEquipmentId),
                            Criteria.where("MCCODE").is(normalizedEquipmentId)
                    )
            );
        }

        return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    private Criteria buildRunDatasetEquipmentCriteria(
            String runId,
            String datasetKey,
            String equipmentId
    ) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("run_id").is(runId));
        criteriaList.add(Criteria.where("dataset_key").is(datasetKey));
        criteriaList.add(buildEquipmentCriteria(equipmentId));
        return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    private Criteria buildDatasetEquipmentCriteria(
            String datasetKey,
            String equipmentId
    ) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("dataset_key").is(datasetKey));
        criteriaList.add(buildEquipmentCriteria(equipmentId));
        return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    private Criteria buildEquipmentCriteria(String equipmentId) {
        return new Criteria().orOperator(
                Criteria.where("equipment_id").is(equipmentId),
                Criteria.where("MCCODE").is(equipmentId)
        );
    }

    public record WindowQuery(
            String runId,
            String datasetKey,
            String equipmentId,
            String status,
            Date from,
            Date to
    ) {
    }
}
