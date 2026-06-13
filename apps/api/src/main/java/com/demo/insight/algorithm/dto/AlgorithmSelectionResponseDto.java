package com.demo.insight.algorithm.dto;

import java.util.List;
import java.util.Map;

public record AlgorithmSelectionResponseDto(
        List<AlgoTypeDto> algoTypes,
        Map<String, List<AlgoOptionDto>> algorithmsByType,
        AlgorithmActiveSelectionDto activeSelection
) {
}
