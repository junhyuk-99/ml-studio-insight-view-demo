package com.demo.insight.healthindex;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class HealthIndexRepository {

    private static final String MODEL_RUN_COLLECTION = "thismodelrun";
    private static final String ANOMALY_RESULT_COLLECTION = "thisanomalyresult";

    private final MongoTemplate mongoTemplate;

    public HealthIndexRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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

    public List<Document> findHealthIndexPoints(TrendQuery trendQuery) {
        Query query = new Query(buildTrendMatchCriteria(trendQuery));
        query.with(Sort.by(Sort.Direction.ASC, "window_start").and(Sort.by(Sort.Direction.ASC, "window_end")));
        query.fields().exclude("_id");
        query.fields()
                .include("run_id")
                .include("dataset_key")
                .include("equipment_id")
                .include("MCCODE")
                .include("window_start")
                .include("window_end")
                .include("health_index")
                .include("anomaly_score")
                .include("status")
                .include("is_anomaly")
                .include("reg_date");
        return mongoTemplate.find(query, Document.class, ANOMALY_RESULT_COLLECTION);
    }

    private Criteria buildTrendMatchCriteria(TrendQuery trendQuery) {
        Criteria criteria = Criteria.where("run_id").is(trendQuery.runId())
                .and("dataset_key").is(trendQuery.datasetKey());

        if (trendQuery.status() != null) {
            criteria.and("status").regex(Pattern.compile("^" + Pattern.quote(trendQuery.status()) + "$", Pattern.CASE_INSENSITIVE));
        }
        if (trendQuery.from() != null) {
            criteria.and("window_start").gte(trendQuery.from());
        }
        if (trendQuery.to() != null) {
            criteria.and("window_end").lte(trendQuery.to());
        }
        return criteria;
    }

    public record TrendQuery(
            String runId,
            String datasetKey,
            String status,
            Date from,
            Date to
    ) {
    }
}
