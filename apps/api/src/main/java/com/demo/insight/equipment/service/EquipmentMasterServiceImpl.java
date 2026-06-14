package com.demo.insight.equipment.service;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.equipment.domain.EquipmentMasterDocument;
import com.demo.insight.equipment.dto.EquipmentMasterDto;
import com.demo.insight.equipment.repository.EquipmentMasterRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EquipmentMasterServiceImpl implements EquipmentMasterService {

    private static final String ACTIVE_FLAG_Y = "Y";
    private static final String AI_USE_FLAG_Y = "Y";
    private static final String LEGACY_GLOBAL_DATASET_KEY = "demo_hmi_all_default_v1";
    private static final String DEPRECATED_RUNTIME_DATASET_KEY = "thisraw_all_default_v1";
    private static final String DEPRECATED_RUNTIME_DATASET_PREFIX = "thisraw_";
    private static final String FALLBACK_OPERATIONAL_DATASET_KEY = "DEMO_DATASET_MANUFACTURING_AI";
    private static final String NORMALIZED_FALLBACK_OPERATIONAL_DATASET_KEY = "demo_dataset_manufacturing_ai";

    private final EquipmentMasterRepository equipmentMasterRepository;
    private final DynamicSchemaResolver schemaResolver;

    public EquipmentMasterServiceImpl(
            EquipmentMasterRepository equipmentMasterRepository,
            DynamicSchemaResolver schemaResolver
    ) {
        this.equipmentMasterRepository = equipmentMasterRepository;
        this.schemaResolver = schemaResolver;
    }

    @Override
    public List<EquipmentMasterDto> getActiveEquipments() {
        return equipmentMasterRepository.findByUseFlagOrderByMccodeAsc(ACTIVE_FLAG_Y).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<EquipmentMasterDto> getAiTargetEquipments() {
        return equipmentMasterRepository.findByUseFlagAndAiUseFlagOrderByMccodeAsc(ACTIVE_FLAG_Y, AI_USE_FLAG_Y).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public Map<String, EquipmentMasterDto> getAiTargetEquipmentByCode() {
        Map<String, EquipmentMasterDto> mapped = new LinkedHashMap<>();
        for (EquipmentMasterDto equipment : getAiTargetEquipments()) {
            if (equipment.mccode() == null || equipment.mccode().isBlank()) {
                continue;
            }
            mapped.put(equipment.mccode().trim().toUpperCase(Locale.ROOT), equipment);
        }
        return Map.copyOf(mapped);
    }

    @Override
    public EquipmentMasterDto findAiTargetEquipment(String equipmentId) {
        String normalizedEquipmentId = normalizeEquipmentId(equipmentId);
        if (normalizedEquipmentId == null) {
            return null;
        }
        return getAiTargetEquipmentByCode().get(normalizedEquipmentId);
    }

    @Override
    public String resolveDatasetKeyByEquipment(String equipmentId) {
        String normalizedEquipmentId = normalizeEquipmentId(equipmentId);
        if (normalizedEquipmentId == null) {
            return null;
        }
        EquipmentMasterDto equipment = findAiTargetEquipment(normalizedEquipmentId);
        if (equipment == null) {
            return null;
        }
        return schemaResolver.buildDemoHmiDatasetKeyForEquipment(normalizedEquipmentId);
    }

    @Override
    public String resolveEquipmentIdByDatasetKey(String datasetKey) {
        String equipmentScope = schemaResolver.resolveEquipmentScopeFromDatasetKey(datasetKey);
        String normalized = normalizeEquipmentId(equipmentScope);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized) || "DEFAULT".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    @Override
    public String resolveDefaultOperationalDatasetKey() {
        String runtimeDefaultDatasetKey = schemaResolver.resolveRuntimeDefaultDatasetKey();
        if (NORMALIZED_FALLBACK_OPERATIONAL_DATASET_KEY.equalsIgnoreCase(runtimeDefaultDatasetKey)
                || FALLBACK_OPERATIONAL_DATASET_KEY.equalsIgnoreCase(runtimeDefaultDatasetKey)) {
            return FALLBACK_OPERATIONAL_DATASET_KEY;
        }

        String runtimeDefaultEquipmentId = normalizeEquipmentId(schemaResolver.resolveRuntimeDefaultPrimaryEquipmentId());
        if (runtimeDefaultEquipmentId != null) {
            String datasetKey = resolveDatasetKeyByEquipment(runtimeDefaultEquipmentId);
            if (datasetKey != null) {
                return datasetKey;
            }
            return FALLBACK_OPERATIONAL_DATASET_KEY;
        }

        return FALLBACK_OPERATIONAL_DATASET_KEY;
    }

    @Override
    public String resolveDefaultOperationalEquipmentId() {
        String runtimeDefaultEquipmentId = normalizeEquipmentId(schemaResolver.resolveRuntimeDefaultPrimaryEquipmentId());
        if (runtimeDefaultEquipmentId != null) {
            return runtimeDefaultEquipmentId;
        }
        String fromDatasetKey = resolveEquipmentIdByDatasetKey(resolveDefaultOperationalDatasetKey());
        if (fromDatasetKey != null) {
            return fromDatasetKey;
        }
        List<EquipmentMasterDto> aiTargetEquipments = getAiTargetEquipments();
        if (!aiTargetEquipments.isEmpty()) {
            return normalizeEquipmentId(aiTargetEquipments.get(0).mccode());
        }
        return null;
    }

    @Override
    public String resolveOpstatCodeGroup(String equipmentId) {
        EquipmentMasterDto equipment = findAiTargetEquipment(equipmentId);
        if (equipment == null) {
            return null;
        }
        return normalizeOptionalText(equipment.opstatCodeGroup());
    }

    @Override
    public boolean isLegacyGlobalDatasetKey(String datasetKey) {
        String normalized = schemaResolver.normalizeDatasetKeyString(datasetKey);
        return normalized != null && LEGACY_GLOBAL_DATASET_KEY.equalsIgnoreCase(normalized);
    }

    @Override
    public boolean isDeprecatedRuntimeDatasetKey(String datasetKey) {
        String normalized = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalized == null) {
            return false;
        }
        if (DEPRECATED_RUNTIME_DATASET_KEY.equalsIgnoreCase(normalized)) {
            return true;
        }
        return normalized.startsWith(DEPRECATED_RUNTIME_DATASET_PREFIX);
    }

    @Override
    public boolean isRuntimeOperationalDatasetKey(String datasetKey) {
        String normalized = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalized == null) {
            return false;
        }
        if (isLegacyGlobalDatasetKey(normalized) || isDeprecatedRuntimeDatasetKey(normalized)) {
            return false;
        }
        String equipmentId = resolveEquipmentIdByDatasetKey(normalized);
        if (equipmentId == null) {
            return false;
        }
        return findAiTargetEquipment(equipmentId) != null;
    }

    private EquipmentMasterDto toDto(EquipmentMasterDocument document) {
        String mccode = normalizeEquipmentId(document.getMccode());
        return new EquipmentMasterDto(
                mccode,
                normalizeOptionalText(document.getMcname()),
                normalizeOptionalText(document.getProcessType()),
                normalizeOptionalText(document.getOpstatCodeGroup()),
                normalizeOptionalText(document.getAiUseFlag()),
                mccode == null ? null : schemaResolver.buildDemoHmiDatasetKeyForEquipment(mccode)
        );
    }

    private String normalizeEquipmentId(String equipmentId) {
        String normalized = normalizeOptionalText(equipmentId);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
