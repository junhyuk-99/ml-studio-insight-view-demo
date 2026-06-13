package com.demo.insight.preprocess.repository;

import com.demo.insight.preprocess.domain.DataTypeDatasetDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DataTypeDatasetRepository extends MongoRepository<DataTypeDatasetDocument, String> {
    List<DataTypeDatasetDocument> findByUseFlagOrderByTypeCodeAscDtlCodeAscSortNoAsc(String useFlag);

    DataTypeDatasetDocument findByDatasetKeyAndUseFlag(String datasetKey, String useFlag);
}
