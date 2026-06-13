package com.demo.insight.equipment.repository;

import com.demo.insight.equipment.domain.EquipmentMasterDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentMasterRepository extends MongoRepository<EquipmentMasterDocument, String> {
    List<EquipmentMasterDocument> findByUseFlagOrderByMccodeAsc(String useFlag);

    List<EquipmentMasterDocument> findByUseFlagAndAiUseFlagOrderByMccodeAsc(String useFlag, String aiUseFlag);

    Optional<EquipmentMasterDocument> findByMccode(String mccode);
}
