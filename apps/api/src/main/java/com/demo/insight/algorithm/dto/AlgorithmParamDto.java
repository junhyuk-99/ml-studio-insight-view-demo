package com.demo.insight.algorithm.dto;

public record AlgorithmParamDto(
        String algoCd,
        String paramCd,
        String paramNm,
        String dataType,
        String requiredYn,
        Object defaultValue,
        Object minValue,
        Object maxValue,
        String uiType,
        Object step,
        String desc,
        Integer sortOrd
) {
}
