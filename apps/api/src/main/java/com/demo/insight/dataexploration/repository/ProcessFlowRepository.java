package com.demo.insight.dataexploration.repository;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Repository
public class ProcessFlowRepository {

    public static final String SOURCE_COLLECTION = "THISHMIDATA";

    private static final String TIMESTAMP_FIELD = "PRDTIME";
    private static final String EQUIPMENT_FIELD = "MCCODE";
    private static final String OPSTAT_FIELD = "OPSTAT";

    private final MongoTemplate mongoTemplate;

    public ProcessFlowRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long countRows(
            String mccode,
            Date startInclusive,
            Date endInclusive,
            List<Object> opstatFilterValues
    ) {
        Query query = buildQuery(mccode, startInclusive, endInclusive, opstatFilterValues);
        return mongoTemplate.count(query, SOURCE_COLLECTION);
    }

    public List<Document> findRows(
            String mccode,
            Date startInclusive,
            Date endInclusive,
            List<Object> opstatFilterValues,
            Set<String> projectedFields,
            int limit
    ) {
        Query query = buildQuery(mccode, startInclusive, endInclusive, opstatFilterValues)
                .with(Sort.by(Sort.Direction.ASC, TIMESTAMP_FIELD).and(Sort.by(Sort.Direction.ASC, "_id")))
                .limit(Math.max(1, limit));

        query.fields().exclude("_id");
        for (String projectedField : projectedFields) {
            if (projectedField == null || projectedField.isBlank()) {
                continue;
            }
            query.fields().include(projectedField.trim());
        }

        return mongoTemplate.find(query, Document.class, SOURCE_COLLECTION);
    }

    private Query buildQuery(
            String mccode,
            Date startInclusive,
            Date endInclusive,
            List<Object> opstatFilterValues
    ) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where(EQUIPMENT_FIELD).is(mccode));
        criteria.add(Criteria.where(TIMESTAMP_FIELD).gte(startInclusive).lte(endInclusive));
        if (opstatFilterValues != null && !opstatFilterValues.isEmpty()) {
            criteria.add(Criteria.where(OPSTAT_FIELD).in(opstatFilterValues));
        }

        Criteria combined = new Criteria().andOperator(criteria.toArray(Criteria[]::new));
        return new Query(combined);
    }
}
