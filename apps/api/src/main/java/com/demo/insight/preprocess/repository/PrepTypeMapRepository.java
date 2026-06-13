package com.demo.insight.preprocess.repository;

import com.demo.insight.preprocess.domain.PrepTypeMapDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PrepTypeMapRepository extends MongoRepository<PrepTypeMapDocument, String> {
    List<PrepTypeMapDocument> findByUseYnOrderByPrepTypeCdAscSortOrdAsc(String useYn);
}

