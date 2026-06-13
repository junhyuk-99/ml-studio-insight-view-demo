package com.demo.insight.preprocess.repository;

import com.demo.insight.preprocess.domain.DataTypeDetailDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DataTypeDetailRepository extends MongoRepository<DataTypeDetailDocument, String> {
    List<DataTypeDetailDocument> findByUseFlagOrderByTypeCodeAscSortNoAsc(String useFlag);
}

