package com.demo.insight.algorithm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AlgorithmParamSaveRequestDto(
        @NotBlank(message = "algoCd is required.")
        String algoCd,
        @NotEmpty(message = "params must not be empty.")
        @Valid
        List<AlgorithmParamSaveItemDto> params
) {
}

