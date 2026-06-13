package com.demo.insight.algorithm.repository;

import com.demo.insight.algorithm.domain.AlgoTypeMasterDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlgoTypeMasterRepository extends MongoRepository<AlgoTypeMasterDocument, String> {
    List<AlgoTypeMasterDocument> findByUseYnOrderBySortOrdAsc(String useYn);
}
