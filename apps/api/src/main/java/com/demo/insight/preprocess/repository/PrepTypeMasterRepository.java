package com.demo.insight.preprocess.repository;

import com.demo.insight.preprocess.domain.PrepTypeMasterDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PrepTypeMasterRepository extends MongoRepository<PrepTypeMasterDocument, String> {
    List<PrepTypeMasterDocument> findByUseYnOrderBySortOrdAsc(String useYn);
}

