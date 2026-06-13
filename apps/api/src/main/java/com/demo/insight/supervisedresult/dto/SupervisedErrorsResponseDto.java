package com.demo.insight.supervisedresult.dto;

import java.util.List;

public record SupervisedErrorsResponseDto(
        String runId,
        List<SupervisedErrorRowDto> fpTop,
        List<SupervisedErrorRowDto> fnTop
) {
}
