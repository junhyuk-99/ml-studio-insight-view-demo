package com.demo.insight.equipment.service;

import com.demo.insight.equipment.dto.EquipmentMasterDto;

import java.util.List;
import java.util.Map;

public interface EquipmentMasterService {
    List<EquipmentMasterDto> getActiveEquipments();

    List<EquipmentMasterDto> getAiTargetEquipments();

    Map<String, EquipmentMasterDto> getAiTargetEquipmentByCode();

    EquipmentMasterDto findAiTargetEquipment(String equipmentId);

    String resolveDatasetKeyByEquipment(String equipmentId);

    String resolveEquipmentIdByDatasetKey(String datasetKey);

    String resolveDefaultOperationalDatasetKey();

    String resolveDefaultOperationalEquipmentId();

    String resolveOpstatCodeGroup(String equipmentId);

    boolean isLegacyGlobalDatasetKey(String datasetKey);

    boolean isDeprecatedRuntimeDatasetKey(String datasetKey);

    boolean isRuntimeOperationalDatasetKey(String datasetKey);
}
