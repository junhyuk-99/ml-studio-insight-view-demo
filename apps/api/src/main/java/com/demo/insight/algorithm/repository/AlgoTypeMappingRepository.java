package com.demo.insight.algorithm.repository;

import com.demo.insight.algorithm.domain.AlgoTypeMappingDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlgoTypeMappingRepository extends MongoRepository<AlgoTypeMappingDocument, String> {
    List<AlgoTypeMappingDocument> findByUseYnOrderByAlgoTypeCdAscSortOrdAsc(String useYn);
}
