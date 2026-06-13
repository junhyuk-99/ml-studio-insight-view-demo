package com.demo.insight.supervisedresult;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Repository
public class SupervisedResultRepository {

    private static final Logger log = LoggerFactory.getLogger(SupervisedResultRepository.class);

    private static final String MODEL_RUN_COLLECTION = "thismodelrun";
    private static final String MODEL_EVAL_COLLECTION = "thismodeleval";
    private static final String CLASSIFICATION_RESULT_COLLECTION = "thisclassificationresult";
    private static final String LABELED_COLLECTION = "thisrawlabeled";

    private static final String ALGO_RANDOM_FOREST = "RANDOM_FOREST";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String DATASET_RF_LABELED_V1 = "thisrawlabeled_all_rf_v1";

    private final MongoTemplate mongoTemplate;

    public SupervisedResultRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Document> findSupervisedRuns(String triggerType, int limit) {
        int safeLimit = Math.max(limit, 1);

        Document match = new Document("run_id", new Document("$exists", true).append("$ne", null))
                .append("algo_code", regexExact(ALGO_RANDOM_FOREST))
                .append("status", regexExact(STATUS_SUCCESS))
                .append("$or", List.of(
                        new Document("doc_type", "RUN"),
                        new Document("doc_type", new Document("$exists", false))
                ));

        if (triggerType != null) {
            match.append("trigger_type", regexExact(triggerType));
        }

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", match));
        pipeline.add(new Document("$sort", new Document("reg_date", -1).append("run_id", -1)));
        pipeline.add(new Document("$limit", safeLimit));
        pipeline.add(new Document("$lookup", new Document("from", CLASSIFICATION_RESULT_COLLECTION)
                .append("let", new Document("runId", "$run_id"))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr", new Document("$eq", List.of("$run_id", "$$runId")))),
                        new Document("$count", "count")
                ))
                .append("as", "classification_meta")));
        pipeline.add(new Document("$lookup", new Document("from", MODEL_EVAL_COLLECTION)
                .append("let", new Document("runId", "$run_id"))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr", new Document("$eq", List.of("$run_id", "$$runId")))),
                        new Document("$sort", new Document("reg_date", -1)),
                        new Document("$limit", 1),
                        new Document("$project", new Document("_id", 0)
                                .append("test_count", 1)
                                .append("total_count", 1))
                ))
                .append("as", "eval_meta")));
        pipeline.add(new Document("$set", new Document("classification_meta", new Document("$first", "$classification_meta"))
                .append("eval_meta", new Document("$first", "$eval_meta"))));
        pipeline.add(new Document("$set", new Document("classification_count", new Document("$ifNull", List.of("$classification_meta.count", 0)))
                .append("executed_at", new Document("$ifNull", List.of("$reg_date", "$updated_at")))
                .append("total_predictions", new Document("$ifNull", List.of("$eval_meta.test_count", "$classification_meta.count", 0L)))));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("run_id", 1)
                .append("dataset_key", 1)
                .append("algo_code", 1)
                .append("algo_name", 1)
                .append("status", 1)
                .append("trigger_type", 1)
                .append("executed_at", 1)
                .append("total_predictions", 1)));

        List<Document> results = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(MODEL_RUN_COLLECTION).aggregate(pipeline).allowDiskUse(true)) {
            results.add(row);
        }
        return results;
    }

    public List<Document> findTrend(String triggerType, int limit) {
        int safeLimit = Math.max(limit, 1);

        List<Document> runLookupPipeline = new ArrayList<>();
        runLookupPipeline.add(new Document("$match", new Document("$expr", new Document("$eq", List.of("$run_id", "$$runId")))));
        runLookupPipeline.add(new Document("$match", new Document("algo_code", regexExact(ALGO_RANDOM_FOREST))
                .append("status", regexExact(STATUS_SUCCESS))
                .append("$or", List.of(
                        new Document("doc_type", "RUN"),
                        new Document("doc_type", new Document("$exists", false))
                ))));
        runLookupPipeline.add(new Document("$sort", new Document("reg_date", -1)));
        runLookupPipeline.add(new Document("$limit", 1));
        runLookupPipeline.add(new Document("$project", new Document("_id", 0)
                .append("trigger_type", 1)
                .append("status", 1)));

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("run_id", new Document("$exists", true).append("$ne", null))
                .append("algo_code", regexExact(ALGO_RANDOM_FOREST))
                .append("dataset_key", regexExact(DATASET_RF_LABELED_V1))));
        pipeline.add(new Document("$lookup", new Document("from", MODEL_RUN_COLLECTION)
                .append("let", new Document("runId", "$run_id"))
                .append("pipeline", runLookupPipeline)
                .append("as", "run_meta")));
        pipeline.add(new Document("$set", new Document("run_meta", new Document("$first", "$run_meta"))));
        pipeline.add(new Document("$match", new Document("run_meta", new Document("$ne", null))));
        if (triggerType != null) {
            pipeline.add(new Document("$match", new Document("run_meta.trigger_type", regexExact(triggerType))));
        }
        pipeline.add(new Document("$sort", new Document("reg_date", -1).append("run_id", -1)));
        pipeline.add(new Document("$limit", safeLimit));
        pipeline.add(new Document("$sort", new Document("reg_date", 1).append("run_id", 1)));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("run_id", 1)
                .append("reg_date", 1)
                .append("trigger_type", "$run_meta.trigger_type")
                .append("accuracy", 1)
                .append("precision", 1)
                .append("recall", 1)
                .append("f1_score", 1)));

        List<Document> results = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(MODEL_EVAL_COLLECTION).aggregate(pipeline).allowDiskUse(true)) {
            results.add(row);
        }
        return results;
    }

    public Document findRunByRunId(String runId) {
        Query query = new Query(
                Criteria.where("run_id").is(runId)
                        .and("algo_code").regex(Pattern.compile("^" + Pattern.quote(ALGO_RANDOM_FOREST) + "$", Pattern.CASE_INSENSITIVE))
                        .andOperator(
                                new Criteria().orOperator(
                                        Criteria.where("doc_type").is("RUN"),
                                        Criteria.where("doc_type").exists(false)
                                )
                        )
        );
        query.fields().exclude("_id");
        return mongoTemplate.findOne(query, Document.class, MODEL_RUN_COLLECTION);
    }

    public Document findLatestModelEvalByRunId(String runId) {
        Query query = new Query(Criteria.where("run_id").is(runId));
        query.fields().exclude("_id");
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "reg_date"));
        query.limit(1);
        return mongoTemplate.findOne(query, Document.class, MODEL_EVAL_COLLECTION);
    }

    public Document aggregateDistributionByRunId(String runId) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("run_id", runId)));
        pipeline.add(new Document("$project", new Document("error_type",
                new Document("$toUpper", new Document("$ifNull", List.of("$error_type", "UNKNOWN"))))));
        pipeline.add(new Document("$group", new Document("_id", "$error_type").append("count", new Document("$sum", 1))));
        pipeline.add(new Document("$group", new Document("_id", null)
                .append("total_count", new Document("$sum", "$count"))
                .append("items", new Document("$push", new Document("error_type", "$_id").append("count", "$count")))));

        Document result = null;
        for (Document row : mongoTemplate.getCollection(CLASSIFICATION_RESULT_COLLECTION).aggregate(pipeline).allowDiskUse(true)) {
            result = row;
            break;
        }
        if (result != null) {
            result.remove("_id");
        }
        return result;
    }

    public List<Document> findTopErrors(String runId, String errorType, int limit) {
        int safeLimit = Math.max(limit, 1);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("run_id", runId).append("error_type", regexExact(errorType))));
        pipeline.add(buildLabeledLookupStage());
        pipeline.add(buildResolvedTimestampStage());
        pipeline.add(new Document("$set", new Document("resolved_probability_anomaly",
                new Document("$ifNull", List.of("$prediction_probability_anomaly", "$prediction_probability", 0D)))));
        if ("FN".equalsIgnoreCase(errorType)) {
            pipeline.add(new Document("$sort", new Document("resolved_probability_anomaly", 1).append("resolved_timestamp", -1)));
        } else {
            pipeline.add(new Document("$sort", new Document("resolved_probability_anomaly", -1).append("resolved_timestamp", -1)));
        }
        pipeline.add(new Document("$limit", safeLimit));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("actual_label", 1)
                .append("prediction_label", 1)
                .append("prediction_probability_anomaly", "$resolved_probability_anomaly")
                .append("prediction_probability_normal", 1)
                .append("prediction_probability", 1)
                .append("input_features", 1)
                .append("resolved_timestamp", 1)));

        List<Document> results = new ArrayList<>();
        try {
            for (Document row : mongoTemplate.getCollection(CLASSIFICATION_RESULT_COLLECTION).aggregate(pipeline).allowDiskUse(true)) {
                results.add(row);
            }
        } catch (RuntimeException runtimeException) {
            log.warn("Failed to load supervised top errors for runId={}, errorType={}. Returning empty list.",
                    runId, errorType, runtimeException);
            return List.of();
        }
        return results;
    }

    public PredictionPageResult findPredictions(PredictionQuery predictionQuery) {
        int safePage = Math.max(predictionQuery.page(), 0);
        int safeSize = Math.max(predictionQuery.size(), 1);
        int skip = safePage * safeSize;

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", buildPredictionMatch(predictionQuery)));
        pipeline.add(buildLabeledLookupStage());
        pipeline.add(buildResolvedTimestampStage());

        if (predictionQuery.from() != null || predictionQuery.to() != null) {
            Document timestampFilter = new Document();
            if (predictionQuery.from() != null) {
                timestampFilter.append("$gte", predictionQuery.from());
            }
            if (predictionQuery.to() != null) {
                timestampFilter.append("$lte", predictionQuery.to());
            }
            pipeline.add(new Document("$match", new Document("resolved_timestamp", timestampFilter)));
        }

        pipeline.add(new Document("$facet", new Document()
                .append("items", List.of(
                        new Document("$sort", new Document("resolved_timestamp", -1).append("reg_date", -1)),
                        new Document("$skip", skip),
                        new Document("$limit", safeSize),
                        new Document("$project", new Document("_id", 0)
                                .append("resolved_timestamp", 1)
                                .append("actual_label", 1)
                                .append("prediction_label", 1)
                                .append("prediction_probability_anomaly",
                                        new Document("$ifNull", List.of("$prediction_probability_anomaly", "$prediction_probability")))
                                .append("prediction_probability_normal", 1)
                                .append("prediction_probability", 1)
                                .append("correct_yn", 1)
                                .append("error_type", 1)
                                .append("input_features", 1))
                ))
                .append("total", List.of(new Document("$count", "count")))));

        Document facetRow = null;
        try {
            for (Document row : mongoTemplate.getCollection(CLASSIFICATION_RESULT_COLLECTION).aggregate(pipeline).allowDiskUse(true)) {
                facetRow = row;
                break;
            }
        } catch (RuntimeException runtimeException) {
            log.warn("Failed to load supervised predictions for runId={}. Returning empty page.",
                    predictionQuery.runId(), runtimeException);
            return new PredictionPageResult(List.of(), 0L);
        }

        if (facetRow == null) {
            return new PredictionPageResult(List.of(), 0L);
        }

        List<Document> items = castDocumentList(facetRow.get("items"));
        List<Document> totalRows = castDocumentList(facetRow.get("total"));
        long total = totalRows.isEmpty() ? 0L : asLong(totalRows.get(0).get("count"));
        return new PredictionPageResult(items, total);
    }

    private Document buildPredictionMatch(PredictionQuery predictionQuery) {
        Document match = new Document("run_id", predictionQuery.runId());
        String filter = predictionQuery.filterType();
        if (filter == null || "ALL".equals(filter)) {
            return match;
        }

        if ("CORRECT".equals(filter)) {
            match.append("correct_yn", regexExact("Y"));
            return match;
        }
        if ("INCORRECT".equals(filter)) {
            match.append("correct_yn", regexExact("N"));
            return match;
        }
        if (List.of("TP", "TN", "FP", "FN").contains(filter)) {
            match.append("error_type", regexExact(filter));
        }
        return match;
    }

    private Document buildLabeledLookupStage() {
        Document sourceIdExpr = toStringOrNull("$source_id");
        Document labeledDocIdExpr = toStringOrNull("$labeled_doc_id");

        return new Document("$lookup", new Document("from", LABELED_COLLECTION)
                .append("let", new Document("sourceId", sourceIdExpr).append("labeledDocId", labeledDocIdExpr))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr", new Document("$or", List.of(
                                new Document("$and", List.of(
                                        new Document("$ne", listAllowNull("$$sourceId", null)),
                                        new Document("$eq", listAllowNull(
                                                toStringOrNull("$source_id"),
                                                "$$sourceId"
                                        ))
                                )),
                                new Document("$and", List.of(
                                        new Document("$ne", listAllowNull("$$labeledDocId", null)),
                                        new Document("$eq", listAllowNull(
                                                toStringOrNull("$_id"),
                                                "$$labeledDocId"
                                        ))
                                ))
                        )))),
                        new Document("$sort", new Document("timestamp", -1)),
                        new Document("$limit", 1),
                        new Document("$project", new Document("_id", 0).append("timestamp", 1))
                ))
                .append("as", "source_meta"));
    }

    private Document toStringOrNull(Object input) {
        return new Document("$convert", new Document("input", input)
                .append("to", "string")
                .append("onError", null)
                .append("onNull", null));
    }

    private List<Object> listAllowNull(Object... values) {
        List<Object> resolved = new ArrayList<>();
        for (Object value : values) {
            resolved.add(value);
        }
        return resolved;
    }

    private Document buildResolvedTimestampStage() {
        return new Document("$set", new Document("source_meta", new Document("$first", "$source_meta"))
                .append("resolved_timestamp", new Document("$let", new Document("vars",
                        new Document("sourceMeta", new Document("$first", "$source_meta")))
                        .append("in", new Document("$ifNull", List.of(
                                "$$sourceMeta.timestamp",
                                "$timestamp",
                                "$reg_date"
                        ))))));
    }

    private List<Document> castDocumentList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Document> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Document document) {
                rows.add(document);
            }
        }
        return rows;
    }

    private Document regexExact(String text) {
        return new Document("$regex", "^" + Pattern.quote(text) + "$")
                .append("$options", "i");
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

    public record PredictionQuery(
            String runId,
            String filterType,
            Date from,
            Date to,
            int page,
            int size
    ) {
    }

    public record PredictionPageResult(
            List<Document> items,
            long total
    ) {
    }
}
