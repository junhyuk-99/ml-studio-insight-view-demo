package com.demo.insight.algorithm.service;

import com.demo.insight.algorithm.domain.AlgoDetailDocument;
import com.demo.insight.algorithm.domain.AlgoTypeMappingDocument;
import com.demo.insight.algorithm.domain.AlgoTypeMasterDocument;
import com.demo.insight.algorithm.dto.AlgorithmActiveSelectionDto;
import com.demo.insight.algorithm.dto.AlgorithmSelectionApplyRequestDto;
import com.demo.insight.algorithm.dto.AlgorithmSelectionApplyResponseDto;
import com.demo.insight.algorithm.dto.AlgoOptionDto;
import com.demo.insight.algorithm.dto.AlgoTypeDto;
import com.demo.insight.algorithm.dto.AlgorithmSelectionResponseDto;
import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.equipment.service.EquipmentMasterService;
import com.demo.insight.algorithm.repository.AlgoDetailRepository;
import com.demo.insight.algorithm.repository.AlgoTypeMappingRepository;
import com.demo.insight.algorithm.repository.AlgoTypeMasterRepository;
import com.demo.insight.modeltrain.repository.ModelTrainRepository;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlgorithmSelectionServiceImpl implements AlgorithmSelectionService {

    private static final String ACTIVE_YN = "Y";
    private static final String LEGACY_GLOBAL_DATASET_KEY = "demo_hmi_all_default_v1";
    private static final String ALGO_ISOLATION_FOREST = "ISOLATION_FOREST";
    private static final String ALGO_AUTOENCODER = "AUTOENCODER";
    private static final String ALGO_RANDOM_FOREST = "RANDOM_FOREST";
    private static final String DEFAULT_CHANGED_BY = "unknown";
    private static final String DEFAULT_CHANGED_REASON = "UI_ALGORITHM_SELECTION_APPLY";

    private final AlgoTypeMasterRepository algoTypeMasterRepository;
    private final AlgoTypeMappingRepository algoTypeMappingRepository;
    private final AlgoDetailRepository algoDetailRepository;
    private final ModelTrainRepository modelTrainRepository;
    private final DynamicSchemaResolver schemaResolver;
    private final EquipmentMasterService equipmentMasterService;

    public AlgorithmSelectionServiceImpl(
            AlgoTypeMasterRepository algoTypeMasterRepository,
            AlgoTypeMappingRepository algoTypeMappingRepository,
            AlgoDetailRepository algoDetailRepository,
            ModelTrainRepository modelTrainRepository,
            DynamicSchemaResolver schemaResolver,
            EquipmentMasterService equipmentMasterService
    ) {
        this.algoTypeMasterRepository = algoTypeMasterRepository;
        this.algoTypeMappingRepository = algoTypeMappingRepository;
        this.algoDetailRepository = algoDetailRepository;
        this.modelTrainRepository = modelTrainRepository;
        this.schemaResolver = schemaResolver;
        this.equipmentMasterService = equipmentMasterService;
    }

    @Override
    public AlgorithmSelectionResponseDto getSelectionOptions(String datasetKey) {
        List<AlgoTypeMasterDocument> typeDocuments = algoTypeMasterRepository.findByUseYnOrderBySortOrdAsc(ACTIVE_YN);
        List<AlgoTypeMappingDocument> mappingDocuments = algoTypeMappingRepository.findByUseYnOrderByAlgoTypeCdAscSortOrdAsc(ACTIVE_YN);

        Set<String> activeAlgoCodes = mappingDocuments.stream()
                .map(AlgoTypeMappingDocument::getAlgoCd)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<AlgoDetailDocument> detailDocuments = activeAlgoCodes.isEmpty()
                ? List.of()
                : algoDetailRepository.findByUseYnAndAlgoCdIn(ACTIVE_YN, new ArrayList<>(activeAlgoCodes));

        Map<String, AlgoDetailDocument> detailMap = detailDocuments.stream()
                .collect(Collectors.toMap(AlgoDetailDocument::getAlgoCd, Function.identity(), (left, right) -> left));

        List<AlgoTypeDto> algoTypes = typeDocuments.stream()
                .map(type -> new AlgoTypeDto(
                        type.getAlgoTypeCd(),
                        type.getAlgoTypeNm(),
                        type.getDesc(),
                        type.getSortOrd()
                ))
                .toList();

        Map<String, List<AlgoOptionDto>> algorithmsByType = new LinkedHashMap<>();
        for (AlgoTypeDto algoType : algoTypes) {
            algorithmsByType.put(algoType.algoTypeCd(), new ArrayList<>());
        }

        for (AlgoTypeMappingDocument mapping : mappingDocuments) {
            List<AlgoOptionDto> options = algorithmsByType.get(mapping.getAlgoTypeCd());
            if (options == null) {
                continue;
            }

            AlgoDetailDocument detail = detailMap.get(mapping.getAlgoCd());
            if (detail == null) {
                continue;
            }

            options.add(new AlgoOptionDto(
                    detail.getAlgoCd(),
                    detail.getAlgoNm(),
                    mapping.getSortOrd()
            ));
        }

        String normalizedDatasetKey = resolveSelectionDatasetKey(datasetKey);
        AlgorithmActiveSelectionDto activeSelection = findActiveSelection(normalizedDatasetKey);
        return new AlgorithmSelectionResponseDto(algoTypes, algorithmsByType, activeSelection);
    }

    @Override
    public AlgorithmSelectionApplyResponseDto applySelection(AlgorithmSelectionApplyRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String algoCode = normalizeRequiredText(request.algoCode(), "algo_code").toUpperCase(Locale.ROOT);

        String requestedDatasetKey = schemaResolver.normalizeDatasetKeyString(request.datasetKey());
        if (requestedDatasetKey == null) {
            throw new IllegalArgumentException("dataset_key is required.");
        }

        String datasetKey = resolveDatasetKeyByAlgorithm(requestedDatasetKey, algoCode);
        if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(datasetKey)) {
            throw new IllegalArgumentException("Deprecated dataset_key is not allowed for runtime algorithm selection: " + datasetKey);
        }
        List<Document> matchedPolicies = modelTrainRepository.findPoliciesByDatasetAndAlgo(datasetKey, algoCode);
        if (matchedPolicies.isEmpty()) {
            if (!isPolicySupportedAlgorithm(algoCode)) {
                throw new IllegalArgumentException("?혙筌왖??혮筌◈???源??혱筌왖 ?혡?? ?혣?⑨＆?귐??혚?혞?? algo_code=" + algoCode);
            }
            throw new IllegalArgumentException(
                    "No active tmst_model_policy found for dataset_key="
                            + datasetKey
                            + ", algo_code="
                            + algoCode
            );
        }

        Document selectedPolicy = selectPrimaryPolicyCandidate(matchedPolicies);
        String activePolicyId = selectedPolicy == null ? null : normalizeOptionalText(selectedPolicy.get("policy_id"));
        if (activePolicyId == null) {
            throw new IllegalStateException("Resolved policy has no policy_id.");
        }

        String activeAlgoName = selectedPolicy == null
                ? algoCode
                : normalizeOptionalText(selectedPolicy.get("algo_name"));
        if (activeAlgoName == null) {
            activeAlgoName = algoCode;
        }

        Map<String, Object> activeFields = new LinkedHashMap<>();
        activeFields.put("active_policy_id", activePolicyId);
        activeFields.put("active_algo_code", algoCode);
        activeFields.put("active_algo_name", activeAlgoName);
        activeFields.put("changed_by", normalizeOrDefault(request.changedBy(), DEFAULT_CHANGED_BY));
        activeFields.put("changed_reason", normalizeOrDefault(request.changedReason(), DEFAULT_CHANGED_REASON));
        activeFields.put("use_flag", ACTIVE_YN);

        modelTrainRepository.upsertModelActive(datasetKey, activeFields);
        Document activeDocument = modelTrainRepository.findLatestEnabledModelActiveByDatasetKey(datasetKey);
        if (activeDocument == null) {
            throw new IllegalStateException("Failed to load persisted active model selection after apply.");
        }
        return toApplyResponse(activeDocument);
    }

    private AlgorithmActiveSelectionDto findActiveSelection(String datasetKey) {
        if (datasetKey == null) {
            return null;
        }

        Document activeDocument = modelTrainRepository.findLatestEnabledModelActiveByDatasetKey(datasetKey);
        if (activeDocument == null) {
            return null;
        }

        String activePolicyId = normalizeOptionalText(activeDocument.get("active_policy_id"));
        Document policyDocument = activePolicyId == null ? null : modelTrainRepository.findPolicyByPolicyId(activePolicyId);

        String activeAlgoCode = normalizeOptionalText(activeDocument.get("active_algo_code"));
        if (activeAlgoCode == null && policyDocument != null) {
            activeAlgoCode = normalizeOptionalText(policyDocument.get("algo_code"));
        }

        String activeAlgoName = normalizeOptionalText(activeDocument.get("active_algo_name"));
        if (activeAlgoName == null && policyDocument != null) {
            activeAlgoName = normalizeOptionalText(policyDocument.get("algo_name"));
        }
        if (activeAlgoName == null) {
            activeAlgoName = activeAlgoCode;
        }

        String updatedAt = normalizeTimestamp(activeDocument.get("updated_at"));
        if (updatedAt == null) {
            updatedAt = normalizeTimestamp(activeDocument.get("reg_date"));
        }

        String activeDatasetKey = schemaResolver.normalizeDatasetKeyString(activeDocument.get("dataset_key"));
        if (activeDatasetKey == null) {
            activeDatasetKey = datasetKey;
        }

        return new AlgorithmActiveSelectionDto(
                activeDatasetKey,
                activePolicyId,
                activeAlgoCode,
                activeAlgoName,
                updatedAt
        );
    }

    private String resolveSelectionDatasetKey(String datasetKey) {
        String normalized = schemaResolver.normalizeDatasetKeyString(datasetKey);
        if (normalized != null) {
            if (equipmentMasterService.isDeprecatedRuntimeDatasetKey(normalized)
                    || equipmentMasterService.isLegacyGlobalDatasetKey(normalized)) {
                return resolveDefaultOperationalDatasetKey();
            }
            return normalized;
        }
        return resolveDefaultOperationalDatasetKey();
    }

    private boolean isPolicySupportedAlgorithm(String algoCode) {
        if (algoCode == null) {
            return false;
        }
        return ALGO_ISOLATION_FOREST.equals(algoCode)
                || ALGO_AUTOENCODER.equals(algoCode)
                || ALGO_RANDOM_FOREST.equals(algoCode);
    }

    private String resolveDatasetKeyByAlgorithm(String requestedDatasetKey, String algoCode) {
        if (ALGO_ISOLATION_FOREST.equals(algoCode)
                || ALGO_AUTOENCODER.equals(algoCode)
                || ALGO_RANDOM_FOREST.equals(algoCode)) {
            return resolveSelectionDatasetKey(requestedDatasetKey);
        }
        return resolveSelectionDatasetKey(requestedDatasetKey);
    }

    private String resolveDefaultDatasetKey() {
        return resolveDefaultOperationalDatasetKey();
    }

    private String resolveDefaultOperationalDatasetKey() {
        String fromEquipmentMaster = equipmentMasterService.resolveDefaultOperationalDatasetKey();
        if (fromEquipmentMaster != null) {
            return fromEquipmentMaster;
        }
        String fromConfig = schemaResolver.resolveRuntimeDefaultDatasetKey();
        if (fromConfig != null) {
            return fromConfig;
        }
        return LEGACY_GLOBAL_DATASET_KEY;
    }

    private Document selectPrimaryPolicyCandidate(List<Document> policies) {
        return policies.stream()
                .sorted(
                        Comparator.comparing(
                                        this::policyPriority,
                                        Comparator.naturalOrder()
                                )
                                .thenComparing(
                                        policy -> normalizeOptionalText(policy.get("policy_id")),
                                        Comparator.nullsLast(String::compareToIgnoreCase)
                                )
                                .thenComparing(
                                        policy -> normalizeTimestamp(policy.get("updated_at")),
                                        Comparator.nullsLast(String::compareTo)
                                )
                )
                .findFirst()
                .orElse(null);
    }

    private int policyPriority(Document policy) {
        String policyId = policy == null ? null : normalizeOptionalText(policy.get("policy_id"));
        if (policyId != null && policyId.toUpperCase(Locale.ROOT).contains("_DEFAULT_")) {
            return 0;
        }
        return 1;
    }

    private AlgorithmSelectionApplyResponseDto toApplyResponse(Document activeDocument) {
        return new AlgorithmSelectionApplyResponseDto(
                normalizeOptionalText(activeDocument.get("dataset_key")),
                normalizeOptionalText(activeDocument.get("active_policy_id")),
                normalizeOptionalText(activeDocument.get("active_algo_code")),
                normalizeOptionalText(activeDocument.get("active_algo_name")),
                normalizeOptionalText(activeDocument.get("changed_by")),
                normalizeOptionalText(activeDocument.get("changed_reason")),
                normalizeOptionalText(activeDocument.get("use_flag")),
                normalizeTimestamp(activeDocument.get("reg_date")),
                normalizeTimestamp(activeDocument.get("updated_at"))
        );
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeOptionalText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeOrDefault(String value, String fallback) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? fallback : normalized;
    }

    private String normalizeTimestamp(Object value) {
        Object normalized = schemaResolver.normalizeResponseValue(value);
        return normalizeOptionalText(normalized);
    }
}
