package com.demo.insight.preprocess.repository;

import com.demo.insight.preprocess.domain.PrepOptionDetailDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PrepOptionDetailRepository extends MongoRepository<PrepOptionDetailDocument, String> {
    List<PrepOptionDetailDocument> findByUseYnAndPrepCdIn(String useYn, List<String> prepCd);
}

