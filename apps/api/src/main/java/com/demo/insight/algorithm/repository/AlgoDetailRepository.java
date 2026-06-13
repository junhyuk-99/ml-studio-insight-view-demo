package com.demo.insight.algorithm.repository;

import com.demo.insight.algorithm.domain.AlgoDetailDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlgoDetailRepository extends MongoRepository<AlgoDetailDocument, String> {
    List<AlgoDetailDocument> findByUseYnAndAlgoCdIn(String useYn, List<String> algoCds);
}
