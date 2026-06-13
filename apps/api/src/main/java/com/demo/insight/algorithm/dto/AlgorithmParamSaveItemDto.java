package com.demo.insight.algorithm.dto;

import jakarta.validation.constraints.NotBlank;

public record AlgorithmParamSaveItemDto(
        @NotBlank(message = "paramCd is required.")
        String paramCd,
        Object defaultValue,
        Object minValue,
        Object maxValue,
        String uiType,
        Object step
) {
}

