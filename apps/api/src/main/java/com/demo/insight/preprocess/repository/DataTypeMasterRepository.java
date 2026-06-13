package com.demo.insight.preprocess.repository;

import com.demo.insight.preprocess.domain.DataTypeMasterDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DataTypeMasterRepository extends MongoRepository<DataTypeMasterDocument, String> {
    List<DataTypeMasterDocument> findByUseFlagOrderBySortNoAsc(String useFlag);
}

