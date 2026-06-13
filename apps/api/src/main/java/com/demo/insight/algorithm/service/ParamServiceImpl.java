package com.demo.insight.algorithm.service;

import com.demo.insight.algorithm.dto.AlgorithmParamDto;
import com.demo.insight.algorithm.dto.AlgorithmParamSaveItemDto;
import com.demo.insight.algorithm.dto.AlgorithmParamSaveRequestDto;
import com.demo.insight.algorithm.dto.AlgorithmParamSaveResponseDto;
import com.demo.insight.algorithm.repository.ParamRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ParamServiceImpl implements ParamService {

    private static final String ACTIVE_YN = "Y";
    private static final String REQUIRED_YN = "Y";
    private static final String DEFAULT_REQUIRED_YN = "N";

    private final ParamRepository paramRepository;

    public ParamServiceImpl(ParamRepository paramRepository) {
        this.paramRepository = paramRepository;
    }

    @Override
    public List<AlgorithmParamDto> getParamsByAlgoCd(String algoCd) {
        if (algoCd == null || algoCd.trim().isEmpty()) {
            throw new IllegalArgumentException("algoCd is required.");
        }
        return paramRepository.findActiveParamsByAlgoCd(algoCd.trim());
    }

    @Override
    public AlgorithmParamSaveResponseDto saveParams(AlgorithmParamSaveRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String algoCd = normalizeRequiredText(request.algoCd(), "algoCd");
        if (request.params() == null || request.params().isEmpty()) {
            throw new IllegalArgumentException("params must not be empty.");
        }

        List<AlgorithmParamDto> activeParams = paramRepository.findActiveParamsByAlgoCd(algoCd);
        if (activeParams.isEmpty()) {
            throw new IllegalArgumentException("No active parameter mapping found for algoCd: " + algoCd);
        }

        Map<String, AlgorithmParamDto> paramMap = activeParams.stream()
                .collect(Collectors.toMap(AlgorithmParamDto::paramCd, Function.identity(), (left, right) -> left));

        Set<String> seenParamCd = new HashSet<>();
        List<ParamRepository.AlgoParamUpsertCommand> commands = new ArrayList<>();

        for (AlgorithmParamSaveItemDto item : request.params()) {
            String paramCd = normalizeRequiredText(item.paramCd(), "paramCd");
            if (!seenParamCd.add(paramCd)) {
                throw new IllegalArgumentException("Duplicate paramCd in request: " + paramCd);
            }

            AlgorithmParamDto param = paramMap.get(paramCd);
            if (param == null) {
                throw new IllegalArgumentException("Unsupported paramCd for algoCd " + algoCd + ": " + paramCd);
            }

            if (!paramRepository.existsActiveParamMaster(paramCd)) {
                throw new IllegalArgumentException("paramCd does not exist in active tmst_param_mst: " + paramCd);
            }

            String dataType = normalizeDataType(param.dataType());
            String requiredYn = normalizeRequiredYn(param.requiredYn());

            Object normalizedDefaultValue = normalizeDefaultValue(dataType, item.defaultValue(), paramCd);
            Object normalizedMinValue = normalizeRangeValue(dataType, item.minValue(), "minValue", paramCd);
            Object normalizedMaxValue = normalizeRangeValue(dataType, item.maxValue(), "maxValue", paramCd);
            Object normalizedStep = normalizeRangeValue(dataType, item.step(), "step", paramCd);

            validateRequiredDefault(requiredYn, normalizedDefaultValue, paramCd);
            validateRangeOrder(normalizedMinValue, normalizedMaxValue, paramCd);
            validateDefaultRange(normalizedDefaultValue, normalizedMinValue, normalizedMaxValue, paramCd);

            String normalizedUiType = normalizeNullableText(item.uiType());

            commands.add(new ParamRepository.AlgoParamUpsertCommand(
                    paramCd,
                    normalizedDefaultValue,
                    normalizedMinValue,
                    normalizedMaxValue,
                    normalizedUiType,
                    normalizedStep,
                    requiredYn,
                    param.sortOrd(),
                    ACTIVE_YN
            ));
        }

        Date now = new Date();
        paramRepository.upsertAlgoParams(algoCd, commands, now);
        return new AlgorithmParamSaveResponseDto(algoCd, commands.size());
    }

    private void validateRequiredDefault(String requiredYn, Object defaultValue, String paramCd) {
        if (!REQUIRED_YN.equals(requiredYn)) {
            return;
        }
        if (isEmptyValue(defaultValue)) {
            throw new IllegalArgumentException("defaultValue is required for required parameter: " + paramCd);
        }
    }

    private void validateRangeOrder(Object minValue, Object maxValue, String paramCd) {
        if (!(minValue instanceof Number minNumber) || !(maxValue instanceof Number maxNumber)) {
            return;
        }
        if (Double.compare(minNumber.doubleValue(), maxNumber.doubleValue()) > 0) {
            throw new IllegalArgumentException("minValue must be less than or equal to maxValue for: " + paramCd);
        }
    }

    private void validateDefaultRange(Object defaultValue, Object minValue, Object maxValue, String paramCd) {
        if (!(defaultValue instanceof Number defaultNumber)) {
            return;
        }

        double value = defaultNumber.doubleValue();
        if (minValue instanceof Number minNumber && Double.compare(value, minNumber.doubleValue()) < 0) {
            throw new IllegalArgumentException("defaultValue must be greater than or equal to minValue for: " + paramCd);
        }
        if (maxValue instanceof Number maxNumber && Double.compare(value, maxNumber.doubleValue()) > 0) {
            throw new IllegalArgumentException("defaultValue must be less than or equal to maxValue for: " + paramCd);
        }
    }

    private Object normalizeDefaultValue(String dataType, Object rawValue, String paramCd) {
        if (isEmptyValue(rawValue)) {
            return null;
        }
        if (!isNumericType(dataType)) {
            return normalizeRequiredText(rawValue.toString(), "defaultValue");
        }
        return parseNumber(rawValue, isIntegerType(dataType), "defaultValue", paramCd);
    }

    private Object normalizeRangeValue(String dataType, Object rawValue, String fieldName, String paramCd) {
        if (isEmptyValue(rawValue)) {
            return null;
        }
        if (!isNumericType(dataType)) {
            return null;
        }
        return parseNumber(rawValue, isIntegerType(dataType), fieldName, paramCd);
    }

    private Number parseNumber(Object rawValue, boolean integerOnly, String fieldName, String paramCd) {
        if (rawValue instanceof Number numberValue) {
            double parsed = numberValue.doubleValue();
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException(fieldName + " must be a valid number for: " + paramCd);
            }
            if (integerOnly && Math.rint(parsed) != parsed) {
                throw new IllegalArgumentException(fieldName + " must be an integer for: " + paramCd);
            }
            return integerOnly ? (long) parsed : parsed;
        }

        String text = normalizeRequiredText(rawValue.toString(), fieldName);
        try {
            if (integerOnly) {
                return Long.parseLong(text);
            }
            double parsed = Double.parseDouble(text);
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException(fieldName + " must be a valid number for: " + paramCd);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid number for: " + paramCd);
        }
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isNumericType(String dataType) {
        String normalized = normalizeDataType(dataType);
        return normalized.equals("int")
                || normalized.equals("integer")
                || normalized.equals("long")
                || normalized.equals("short")
                || normalized.equals("float")
                || normalized.equals("double")
                || normalized.equals("decimal")
                || normalized.equals("number");
    }

    private boolean isIntegerType(String dataType) {
        String normalized = normalizeDataType(dataType);
        return normalized.equals("int")
                || normalized.equals("integer")
                || normalized.equals("long")
                || normalized.equals("short");
    }

    private String normalizeDataType(String dataType) {
        if (dataType == null) {
            return "";
        }
        return dataType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredYn(String requiredYn) {
        if (requiredYn == null) {
            return DEFAULT_REQUIRED_YN;
        }
        String normalized = requiredYn.trim().toUpperCase(Locale.ROOT);
        return Objects.equals(normalized, REQUIRED_YN) ? REQUIRED_YN : DEFAULT_REQUIRED_YN;
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String textValue) {
            return textValue.trim().isEmpty();
        }
        return false;
    }
}
