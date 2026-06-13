package com.demo.insight.home.repository;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Repository
public class HomeDashboardRepository {

    private static final String MODEL_ACTIVE_COLLECTION = "tmst_model_active";
    private static final String MODEL_POLICY_COLLECTION = "tmst_model_policy";
    private static final String MODEL_RUN_COLLECTION = "thismodelrun";
    private static final String ANOMALY_RESULT_COLLECTION = "thisanomalyresult";
    private static final String CLASSIFICATION_RESULT_COLLECTION = "thisclassificationresult";
    private static final String MODEL_EVAL_COLLECTION = "thismodeleval";
    private static final String DATA_TYPE_COLLECTION = "tmst_data_type";

    private final MongoTemplate mongoTemplate;

    public HomeDashboardRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Document> findEnabledModelActives() {
        Query query = new Query(activeUseFlagCriteria("use_flag", "useflag", "USE_FLAG"));
        query.with(
                Sort.by(Sort.Direction.DESC, "updated_at")
                        .and(Sort.by(Sort.Direction.DESC, "reg_date"))
                        .and(Sort.by(Sort.Direction.ASC, "dataset_key"))
        );
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, MODEL_ACTIVE_COLLECTION);
    }

    public Document findPolicyByPolicyId(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return null;
        }

        Query query = new Query(Criteria.where("policy_id").is(policyId.trim()));
        query.with(Sort.by(Sort.Direction.DESC, "updated_at").and(Sort.by(Sort.Direction.DESC, "reg_date")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_POLICY_COLLECTION);
    }

    public Document findLatestModelRun() {
        Query query = new Query(runDocTypeCriteria());
        query.with(Sort.by(Sort.Direction.DESC, "reg_date").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public List<Document> findRecentModelRuns(int limit) {
        int safeLimit = Math.max(limit, 1);
        Query query = new Query(runDocTypeCriteria());
        query.with(Sort.by(Sort.Direction.DESC, "reg_date").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.limit(safeLimit);
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public Document findLatestModelRunByAlgoCode(String algoCode) {
        if (algoCode == null || algoCode.isBlank()) {
            return null;
        }

        Query query = new Query(runDocTypeCriteria());
        query.addCriteria(Criteria.where("algo_code").regex(exactIgnoreCasePattern(algoCode.trim())));
        query.with(Sort.by(Sort.Direction.DESC, "reg_date").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public long countAnomalyResultsByRunId(String runId) {
        String normalizedRunId = normalizeText(runId);
        if (normalizedRunId == null) {
            return 0L;
        }
        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        return mongoTemplate.count(query, ANOMALY_RESULT_COLLECTION);
    }

    public long countAnomalyAlertResultsByRunId(String runId) {
        String normalizedRunId = normalizeText(runId);
        if (normalizedRunId == null) {
            return 0L;
        }
        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("is_anomaly").is(true),
                Criteria.where("status").regex(exactAnyIgnoreCasePattern("WARNING", "CRITICAL"))
        ));
        return mongoTemplate.count(query, ANOMALY_RESULT_COLLECTION);
    }

    public long countClassificationResultsByRunId(String runId) {
        String normalizedRunId = normalizeText(runId);
        if (normalizedRunId == null) {
            return 0L;
        }
        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        return mongoTemplate.count(query, CLASSIFICATION_RESULT_COLLECTION);
    }

    public long countClassificationAnomalyResultsByRunId(String runId) {
        String normalizedRunId = normalizeText(runId);
        if (normalizedRunId == null) {
            return 0L;
        }
        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        query.addCriteria(Criteria.where("prediction_label").in(1, "1"));
        return mongoTemplate.count(query, CLASSIFICATION_RESULT_COLLECTION);
    }

    public Document findLatestModelEvalByRunId(String runId) {
        String normalizedRunId = normalizeText(runId);
        if (normalizedRunId == null) {
            return null;
        }
        Query query = new Query(Criteria.where("run_id").is(normalizedRunId));
        query.with(Sort.by(Sort.Direction.DESC, "reg_date").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_EVAL_COLLECTION);
    }

    public Map<String, Long> aggregateAnomalyTrendCounts(Date fromInclusive, ZoneId zoneId) {
        if (fromInclusive == null) {
            return Map.of();
        }

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("$or", List.of(
                new Document("is_anomaly", true),
                new Document("status", regexAnyIgnoreCase("WARNING", "CRITICAL"))
        ))));
        pipeline.add(new Document("$set", new Document("event_time", toDateOrNullExpression(
                new Document("$ifNull", List.of("$window_end", "$reg_date"))
        ))));
        pipeline.add(new Document("$match", new Document("event_time",
                new Document("$ne", null).append("$gte", fromInclusive))));
        pipeline.add(new Document("$project", new Document("day", new Document("$dateToString", new Document()
                .append("format", "%Y-%m-%d")
                .append("date", "$event_time")
                .append("timezone", zoneId.getId())
        ))));
        pipeline.add(new Document("$group", new Document("_id", "$day").append("count", new Document("$sum", 1))));
        pipeline.add(new Document("$sort", new Document("_id", 1)));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Document row : mongoTemplate.getCollection(ANOMALY_RESULT_COLLECTION).aggregate(pipeline).allowDiskUse(true)) {
            String day = normalizeText(row.get("_id"));
            if (day == null) {
                continue;
            }
            counts.put(day, asLong(row.get("count")));
        }
        return counts;
    }

    public List<Document> findActiveDatasetTypes() {
        Query query = new Query(activeUseFlagCriteria("USE_FLAG", "use_flag", "useflag"));
        query.with(Sort.by(Sort.Direction.ASC, "SORT_NO").and(Sort.by(Sort.Direction.ASC, "DATASET_KEY")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, DATA_TYPE_COLLECTION);
    }

    public Document findLatestAnomalyResult() {
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.DESC, "reg_date").and(Sort.by(Sort.Direction.DESC, "window_end")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, ANOMALY_RESULT_COLLECTION);
    }

    public Document findLatestClassificationResult() {
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.DESC, "reg_date"));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, CLASSIFICATION_RESULT_COLLECTION);
    }

    public Document findLatestModelEval() {
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.DESC, "updated_at").and(Sort.by(Sort.Direction.DESC, "reg_date")));
        query.limit(1);
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_EVAL_COLLECTION);
    }

    private Criteria activeUseFlagCriteria(String... candidateFields) {
        List<Criteria> criteriaParts = new ArrayList<>();
        for (String candidateField : candidateFields) {
            if (candidateField == null || candidateField.isBlank()) {
                continue;
            }
            criteriaParts.add(Criteria.where(candidateField).regex(exactIgnoreCasePattern("Y")));
        }
        if (criteriaParts.isEmpty()) {
            return new Criteria();
        }
        if (criteriaParts.size() == 1) {
            return criteriaParts.get(0);
        }
        return new Criteria().orOperator(criteriaParts.toArray(Criteria[]::new));
    }

    private Criteria runDocTypeCriteria() {
        return new Criteria().orOperator(
                Criteria.where("doc_type").is("RUN"),
                Criteria.where("doc_type").exists(false)
        );
    }

    private Pattern exactIgnoreCasePattern(String value) {
        return Pattern.compile("^" + Pattern.quote(value) + "$", Pattern.CASE_INSENSITIVE);
    }

    private Pattern exactAnyIgnoreCasePattern(String... values) {
        if (values == null || values.length == 0) {
            return exactIgnoreCasePattern("");
        }

        List<String> escapedValues = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            escapedValues.add(Pattern.quote(value.trim()));
        }

        if (escapedValues.isEmpty()) {
            return exactIgnoreCasePattern("");
        }

        String pattern = "^(" + String.join("|", escapedValues) + ")$";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
    }

    private Document regexAnyIgnoreCase(String... values) {
        Pattern pattern = exactAnyIgnoreCasePattern(values);
        return new Document("$regex", pattern.pattern()).append("$options", "i");
    }

    private Document toDateOrNullExpression(Object inputExpression) {
        return new Document("$convert", new Document("input", inputExpression)
                .append("to", "date")
                .append("onError", null)
                .append("onNull", null));
    }

    private String normalizeText(Object value) {
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
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return 0L;
            }
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
