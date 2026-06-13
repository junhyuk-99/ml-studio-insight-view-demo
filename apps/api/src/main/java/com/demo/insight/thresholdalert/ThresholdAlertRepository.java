package com.demo.insight.thresholdalert;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
public class ThresholdAlertRepository {

    private static final String RULE_COLLECTION = "tmst_threshold_rule";
    private static final String ALERT_COLLECTION = "thisthresholdalert";
    private static final String ANOMALY_RESULT_COLLECTION = "thisanomalyresult";

    private static final String TARGET_COLLECTION_ANOMALY_RESULT = "thisanomalyresult";
    private static final String TARGET_TYPE_HEALTH_INDEX = "HEALTH_INDEX";
    private static final String TARGET_FIELD_HEALTH_INDEX = "health_index";

    private final MongoTemplate mongoTemplate;
    private final DynamicSchemaResolver schemaResolver;

    public ThresholdAlertRepository(
            MongoTemplate mongoTemplate,
            DynamicSchemaResolver schemaResolver
    ) {
        this.mongoTemplate = mongoTemplate;
        this.schemaResolver = schemaResolver;
    }

    public List<Document> findActiveHealthIndexRules(String datasetKey) {
        String normalizedDatasetKey = schemaResolver.normalizeDatasetKeyString(datasetKey);

        Query query = new Query(
                Criteria.where("useflag").regex("^y$", "i")
                        .and("target_collection").regex("^" + Pattern.quote(TARGET_COLLECTION_ANOMALY_RESULT) + "$", "i")
                        .and("target_type").regex("^" + Pattern.quote(TARGET_TYPE_HEALTH_INDEX) + "$", "i")
                        .andOperator(buildRuleDatasetCriteria(normalizedDatasetKey))
        );
        query.with(Sort.by(Sort.Direction.ASC, "sortno").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, RULE_COLLECTION);
    }

    public List<Document> findAnomalyResultsByRunAndDataset(String runId, String datasetKey) {
        Query query = new Query(
                Criteria.where("run_id").is(runId)
                        .and("dataset_key").is(datasetKey)
        );
        query.with(Sort.by(Sort.Direction.ASC, "window_start").and(Sort.by(Sort.Direction.ASC, "window_end")));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, ANOMALY_RESULT_COLLECTION);
    }

    public long countAlerts(AlertListQuery listQuery) {
        Query query = new Query(buildAlertMatchCriteria(listQuery));
        return mongoTemplate.count(query, ALERT_COLLECTION);
    }

    public List<Document> findAlerts(AlertListQuery listQuery, int skip, int limit) {
        Query query = new Query(buildAlertMatchCriteria(listQuery));
        query.with(Sort.by(Sort.Direction.DESC, "created_at").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.skip(Math.max(skip, 0));
        query.limit(Math.max(limit, 1));
        query.fields().exclude("_id");
        return mongoTemplate.find(query, Document.class, ALERT_COLLECTION);
    }

    public List<Document> findTrend(AlertListQuery listQuery, int limit) {
        Query query = new Query(buildAlertMatchCriteria(listQuery));
        query.with(
                Sort.by(Sort.Direction.ASC, "window_start")
                        .and(Sort.by(Sort.Direction.ASC, "created_at"))
                        .and(Sort.by(Sort.Direction.ASC, "updated_at"))
        );
        query.limit(Math.max(limit, 1));
        query.fields().exclude("_id");
        query.fields()
                .include("alert_id")
                .include("dataset_key")
                .include("run_id")
                .include("window_start")
                .include("window_end")
                .include("created_at")
                .include("display_name")
                .include("target_type")
                .include("target_field")
                .include("value")
                .include("value_scale")
                .include("severity")
                .include("warning_value")
                .include("critical_value")
                .include("status")
                .include("ack_yn")
                .include("ack_by")
                .include("ack_at");
        return mongoTemplate.find(query, Document.class, ALERT_COLLECTION);
    }

    public Document aggregateAlertSummary(AlertSummaryQuery summaryQuery) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", buildAlertSummaryCriteria(summaryQuery).getCriteriaObject()));
        pipeline.add(
                new Document("$group", new Document("_id", null)
                        .append("total_count", new Document("$sum", 1))
                        .append("open_count", new Document("$sum", new Document("$cond", List.of(
                                new Document("$eq", List.of(new Document("$toUpper", new Document("$ifNull", List.of("$status", ""))), "OPEN")),
                                1,
                                0
                        ))))
                        .append("ack_count", new Document("$sum", new Document("$cond", List.of(
                                new Document("$eq", List.of(new Document("$toUpper", new Document("$ifNull", List.of("$status", ""))), "ACK")),
                                1,
                                0
                        ))))
                        .append("warning_count", new Document("$sum", new Document("$cond", List.of(
                                new Document("$eq", List.of(new Document("$toUpper", new Document("$ifNull", List.of("$severity", ""))), "WARNING")),
                                1,
                                0
                        ))))
                        .append("critical_count", new Document("$sum", new Document("$cond", List.of(
                                new Document("$eq", List.of(new Document("$toUpper", new Document("$ifNull", List.of("$severity", ""))), "CRITICAL")),
                                1,
                                0
                        ))))
                        .append("latest_alert_at", new Document("$max", "$created_at"))
                )
        );

        Document summary = null;
        for (Document row : mongoTemplate.getCollection(ALERT_COLLECTION)
                .aggregate(pipeline)
                .allowDiskUse(true)) {
            summary = row;
            break;
        }

        if (summary == null) {
            return null;
        }
        summary.remove("_id");
        return summary;
    }

    public Document findLatestAlert(AlertSummaryQuery summaryQuery) {
        Query query = new Query(buildAlertSummaryCriteria(summaryQuery));
        query.with(Sort.by(Sort.Direction.DESC, "created_at").and(Sort.by(Sort.Direction.DESC, "updated_at")));
        query.limit(1);
        query.fields().exclude("_id");
        query.fields().include("created_at").include("severity").include("display_name");
        return mongoTemplate.findOne(query, Document.class, ALERT_COLLECTION);
    }

    public Document upsertThresholdAlert(Document alertDocument) {
        String ruleId = normalizeRequiredText(alertDocument.get("rule_id"), "rule_id");
        String datasetKey = normalizeRequiredText(alertDocument.get("dataset_key"), "dataset_key");
        String runId = normalizeRequiredText(alertDocument.get("run_id"), "run_id");
        String targetField = normalizeRequiredText(alertDocument.get("target_field"), "target_field");
        Date windowStart = asDate(alertDocument.get("window_start"));
        Date windowEnd = asDate(alertDocument.get("window_end"));
        String equipmentId = normalizeOptionalText(alertDocument.get("equipment_id"));
        if (equipmentId == null) {
            equipmentId = normalizeOptionalText(alertDocument.get("MCCODE"));
        }
        if (windowStart == null || windowEnd == null) {
            throw new IllegalArgumentException("window_start and window_end are required Date fields.");
        }

        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("rule_id").is(ruleId));
        criteriaList.add(Criteria.where("dataset_key").is(datasetKey));
        criteriaList.add(Criteria.where("run_id").is(runId));
        criteriaList.add(Criteria.where("window_start").is(windowStart));
        criteriaList.add(Criteria.where("window_end").is(windowEnd));
        criteriaList.add(Criteria.where("target_field").is(targetField));
        if (equipmentId != null) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("equipment_id").is(equipmentId),
                    Criteria.where("MCCODE").is(equipmentId)
            ));
        }
        Query query = new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

        Document existing = mongoTemplate.findOne(query, Document.class, ALERT_COLLECTION);

        Date now = asDate(alertDocument.get("updated_at"));
        if (now == null) {
            now = new Date();
        }

        String nextStatus = normalizeOptionalText(alertDocument.get("status"));
        if (nextStatus == null) {
            nextStatus = "OPEN";
        }
        nextStatus = nextStatus.toUpperCase(Locale.ROOT);

        String nextAckYn = normalizeOptionalText(alertDocument.get("ack_yn"));
        if (nextAckYn == null) {
            nextAckYn = "n";
        }
        nextAckYn = nextAckYn.toLowerCase(Locale.ROOT);

        Object nextAckBy = alertDocument.get("ack_by");
        Object nextAckAt = alertDocument.get("ack_at");
        Object nextMemo = alertDocument.get("memo");

        if (existing != null) {
            String existingStatus = normalizeOptionalText(existing.get("status"));
            String existingAckYn = normalizeOptionalText(existing.get("ack_yn"));
            if (existingAckYn == null) {
                existingAckYn = existing.get("ack_at") != null || normalizeOptionalText(existing.get("ack_by")) != null ? "y" : "n";
            }

            nextStatus = existingStatus == null
                    ? ("y".equalsIgnoreCase(existingAckYn) ? "ACK" : "OPEN")
                    : existingStatus.toUpperCase(Locale.ROOT);
            nextAckYn = existingAckYn.toLowerCase(Locale.ROOT);
            nextAckBy = existing.get("ack_by");
            nextAckAt = existing.get("ack_at");
            nextMemo = existing.get("memo");
        }

        Update update = new Update();
        update.set("rule_id", ruleId);
        update.set("dataset_key", datasetKey);
        update.set("run_id", runId);
        update.set("window_start", windowStart);
        update.set("window_end", windowEnd);
        if (equipmentId != null) {
            update.set("equipment_id", equipmentId);
            update.set("MCCODE", equipmentId);
        }
        update.set("target_collection", alertDocument.get("target_collection"));
        update.set("target_type", alertDocument.get("target_type"));
        update.set("target_field", targetField);
        update.set("display_name", alertDocument.get("display_name"));
        update.set("value", alertDocument.get("value"));
        update.set("severity", alertDocument.get("severity"));
        update.set("operator", alertDocument.get("operator"));
        update.set("warning_value", alertDocument.get("warning_value"));
        update.set("critical_value", alertDocument.get("critical_value"));
        update.set("status", nextStatus);
        update.set("ack_yn", nextAckYn);
        update.set("ack_by", nextAckBy);
        update.set("ack_at", nextAckAt);
        update.set("memo", nextMemo);
        update.set("updated_at", now);

        String alertId = normalizeOptionalText(alertDocument.get("alert_id"));
        if (alertId != null && existing == null) {
            update.setOnInsert("alert_id", alertId);
        }

        Date createdAt = asDate(alertDocument.get("created_at"));
        update.setOnInsert("created_at", createdAt == null ? now : createdAt);

        Document updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                ALERT_COLLECTION
        );

        if (updated == null) {
            throw new IllegalStateException("Failed to upsert threshold alert document.");
        }

        // ?혚 alert ?????源껊궗 ?혵 anomalyresult?혱 alert_generated ?혣?혱域밸챶? true嚥≤?揶쏄퉮혢田?
        markAlertGenerated(runId, datasetKey, equipmentId, windowStart, windowEnd);

        updated.remove("_id");
        return updated;
    }

    public Document ackAlert(String alertId, String ackBy, String memo, Date ackAt) {
        Query query = new Query(Criteria.where("alert_id").is(alertId));

        Update update = new Update()
                .set("status", "ACK")
                .set("ack_yn", "y")
                .set("ack_by", ackBy)
                .set("ack_at", ackAt)
                .set("memo", memo)
                .set("updated_at", ackAt);

        Document updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Document.class,
                ALERT_COLLECTION
        );

        if (updated != null) {
            updated.remove("_id");
        }
        return updated;
    }

    public List<Document> findMissingHealthIndexAlertTargets(int limit) {
        int safeLimit = Math.max(limit, 1);

        Document matchDoc = new Document()
                .append("run_id", new Document("$exists", true).append("$ne", null))
                .append("dataset_key", new Document("$exists", true).append("$ne", null))
                .append("window_start", new Document("$exists", true).append("$ne", null))
                .append("window_end", new Document("$exists", true).append("$ne", null))
                .append(TARGET_FIELD_HEALTH_INDEX, new Document("$exists", true).append("$ne", null))
                .append("alert_generated", new Document("$ne", true));

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
                .append("health_index", 1)
                .append("status", 1)
                .append("reg_date", 1)));

        List<Document> results = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(ANOMALY_RESULT_COLLECTION)
                .aggregate(pipeline)
                .maxTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .allowDiskUse(true)) {
            results.add(row);
        }
        return results;
    }

    /**
     * thisanomalyresult ?얜챷혙혵?혨 alert_generated = true ?혣?혱域밸챶? ?紐끒뙿?혵??
     * findMissingHealthIndexAlertTargets ?혨?혵 $lookup ?혛????＆?쒕떯? 沃섎챷???椰꾨똻혶혙 鈺곌퀬혳혣?혱疫??혙?혵 ??맞뤒?
     */
    private void markAlertGenerated(String runId, String datasetKey, String equipmentId, Date windowStart, Date windowEnd) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("run_id").is(runId));
        criteriaList.add(Criteria.where("dataset_key").is(datasetKey));
        criteriaList.add(Criteria.where("window_start").is(windowStart));
        criteriaList.add(Criteria.where("window_end").is(windowEnd));
        if (equipmentId != null) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("equipment_id").is(equipmentId),
                    Criteria.where("MCCODE").is(equipmentId)
            ));
        }
        Query resultQuery = new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        mongoTemplate.updateFirst(
                resultQuery,
                new Update()
                        .set("alert_generated", true)
                        .set("updated_at", new Date()),
                ANOMALY_RESULT_COLLECTION
        );
    }

    private Criteria buildAlertMatchCriteria(AlertListQuery listQuery) {
        Criteria criteria = Criteria.where("dataset_key").is(listQuery.datasetKey());

        if (listQuery.runId() != null) {
            criteria.and("run_id").is(listQuery.runId());
        }
        if (listQuery.severity() != null) {
            criteria.and("severity").regex(Pattern.compile("^" + Pattern.quote(listQuery.severity()) + "$", Pattern.CASE_INSENSITIVE));
        }
        if (listQuery.status() != null) {
            criteria.and("status").regex(Pattern.compile("^" + Pattern.quote(listQuery.status()) + "$", Pattern.CASE_INSENSITIVE));
        }
        if (listQuery.ackYn() != null) {
            criteria.and("ack_yn").regex(Pattern.compile("^" + Pattern.quote(listQuery.ackYn()) + "$", Pattern.CASE_INSENSITIVE));
        }
        if (listQuery.from() != null) {
            criteria.and("created_at").gte(listQuery.from());
        }
        if (listQuery.to() != null) {
            criteria.and("created_at").lte(listQuery.to());
        }

        return criteria;
    }

    private Criteria buildAlertSummaryCriteria(AlertSummaryQuery summaryQuery) {
        Criteria criteria = Criteria.where("dataset_key").is(summaryQuery.datasetKey());
        if (summaryQuery.runId() != null) {
            criteria.and("run_id").is(summaryQuery.runId());
        }
        if (summaryQuery.from() != null) {
            criteria.and("created_at").gte(summaryQuery.from());
        }
        if (summaryQuery.to() != null) {
            criteria.and("created_at").lte(summaryQuery.to());
        }
        return criteria;
    }

    private Criteria buildRuleDatasetCriteria(String datasetKey) {
        if (datasetKey == null) {
            return new Criteria();
        }

        return new Criteria().orOperator(
                Criteria.where("dataset_key").is(datasetKey),
                Criteria.where("dataset_key").exists(false),
                Criteria.where("dataset_key").is(null),
                Criteria.where("dataset_key").is("")
        );
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

    private Date asDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date dateValue) {
            return dateValue;
        }
        if (value instanceof Instant instantValue) {
            return Date.from(instantValue);
        }
        if (value instanceof Number numberValue) {
            return new Date(numberValue.longValue());
        }

        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Date.from(Instant.parse(normalized));
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record AlertListQuery(
            String datasetKey,
            String runId,
            String severity,
            String status,
            String ackYn,
            Date from,
            Date to
    ) {
    }

    public record AlertSummaryQuery(
            String datasetKey,
            String runId,
            Date from,
            Date to
    ) {
    }
}
