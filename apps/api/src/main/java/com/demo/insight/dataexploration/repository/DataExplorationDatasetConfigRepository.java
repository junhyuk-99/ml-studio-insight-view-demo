package com.demo.insight.dataexploration.repository;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class DataExplorationDatasetConfigRepository {

    private static final String COLLECTION = "tmst_dataset_config";
    private static final String ACTIVE_FLAG_Y = "Y";

    private final MongoTemplate mongoTemplate;

    public DataExplorationDatasetConfigRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Document findActiveByDatasetKey(String datasetKey) {
        if (datasetKey == null || datasetKey.isBlank()) {
            return null;
        }

        Query query = Query.query(
                new Criteria().andOperator(
                        datasetKeyCriteria(datasetKey.trim()),
                        activeFlagCriteria()
                )
        );
        query.with(Sort.by(Sort.Direction.DESC, "updated_at").and(Sort.by(Sort.Direction.DESC, "reg_date")));
        query.limit(1);
        return mongoTemplate.findOne(query, Document.class, COLLECTION);
    }

    private Criteria datasetKeyCriteria(String datasetKey) {
        List<Criteria> variants = new ArrayList<>();
        variants.add(Criteria.where("dataset_key").is(datasetKey));
        variants.add(Criteria.where("DATASET_KEY").is(datasetKey));
        return new Criteria().orOperator(variants.toArray(Criteria[]::new));
    }

    private Criteria activeFlagCriteria() {
        List<Criteria> variants = new ArrayList<>();
        variants.add(Criteria.where("use_flag").is(ACTIVE_FLAG_Y));
        variants.add(Criteria.where("USE_FLAG").is(ACTIVE_FLAG_Y));
        variants.add(Criteria.where("use_yn").is(ACTIVE_FLAG_Y));
        variants.add(Criteria.where("USE_YN").is(ACTIVE_FLAG_Y));
        return new Criteria().orOperator(variants.toArray(Criteria[]::new));
    }
}
