package com.demo.insight.algorithm.repository;

import com.demo.insight.algorithm.dto.AlgorithmParamDto;
import com.mongodb.bulk.BulkWriteResult;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public class ParamRepository {

    private static final String ACTIVE_YN = "Y";
    private static final String MAP_COLLECTION = "tmst_map_algo_param";
    private static final String PARAM_COLLECTION = "tmst_param_mst";

    private final MongoTemplate mongoTemplate;

    public ParamRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<AlgorithmParamDto> findActiveParamsByAlgoCd(String algoCd) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("algoCd").is(algoCd).and("useYn").is(ACTIVE_YN)),
                Aggregation.lookup(PARAM_COLLECTION, "paramCd", "paramCd", "param"),
                Aggregation.unwind("param"),
                Aggregation.match(Criteria.where("param.useYn").is(ACTIVE_YN)),
                Aggregation.sort(Sort.by(
                        Sort.Order.asc("sortOrd"),
                        Sort.Order.asc("param.sortOrd"),
                        Sort.Order.asc("param.paramCd")
                )),
                Aggregation.project()
                        .and("algoCd").as("algoCd")
                        .and("param.paramCd").as("paramCd")
                        .and("param.paramNm").as("paramNm")
                        .and("param.dataType").as("dataType")
                        .and("requiredYn").as("requiredYn")
                        .and(ConditionalOperators.ifNull("defaultValue").thenValueOf("param.defaultValue")).as("defaultValue")
                        .and(ConditionalOperators.ifNull("minValue").thenValueOf("param.minValue")).as("minValue")
                        .and(ConditionalOperators.ifNull("maxValue").thenValueOf("param.maxValue")).as("maxValue")
                        .and("uiType").as("uiType")
                        .and("step").as("step")
                        .and("param.desc").as("desc")
                        .and("sortOrd").as("sortOrd")
        );

        AggregationResults<AlgorithmParamDto> results =
                mongoTemplate.aggregate(aggregation, MAP_COLLECTION, AlgorithmParamDto.class);
        return results.getMappedResults();
    }

    public int upsertAlgoParams(String algoCd, List<AlgoParamUpsertCommand> commands, Date timestamp) {
        if (commands.isEmpty()) {
            return 0;
        }

        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MAP_COLLECTION);

        for (AlgoParamUpsertCommand command : commands) {
            Query query = Query.query(Criteria.where("algoCd").is(algoCd).and("paramCd").is(command.paramCd()));
            Update update = new Update()
                    .set("defaultValue", command.defaultValue())
                    .set("minValue", command.minValue())
                    .set("maxValue", command.maxValue())
                    .set("uiType", command.uiType())
                    .set("step", command.step())
                    .set("updatedAt", timestamp)
                    .setOnInsert("algoCd", algoCd)
                    .setOnInsert("paramCd", command.paramCd())
                    .setOnInsert("requiredYn", command.requiredYn())
                    .setOnInsert("sortOrd", command.sortOrd())
                    .setOnInsert("useYn", command.useYn())
                    .setOnInsert("createdAt", timestamp);
            bulkOperations.upsert(query, update);
        }

        BulkWriteResult writeResult = bulkOperations.execute();
        return (int) (writeResult.getModifiedCount() + writeResult.getUpserts().size());
    }

    public boolean existsActiveParamMaster(String paramCd) {
        Query query = Query.query(Criteria.where("paramCd").is(paramCd).and("useYn").is(ACTIVE_YN));
        return mongoTemplate.exists(query, PARAM_COLLECTION);
    }

    public record AlgoParamUpsertCommand(
            String paramCd,
            Object defaultValue,
            Object minValue,
            Object maxValue,
            String uiType,
            Object step,
            String requiredYn,
            Integer sortOrd,
            String useYn
    ) {
    }
}
