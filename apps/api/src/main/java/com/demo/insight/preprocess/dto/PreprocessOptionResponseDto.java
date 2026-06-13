package com.demo.insight.preprocess.dto;

import java.util.List;
import java.util.Map;

public record PreprocessOptionResponseDto(
        List<PrepTypeDto> prepTypes,
        Map<String, List<PrepOptionDto>> optionsByType
) {
}

