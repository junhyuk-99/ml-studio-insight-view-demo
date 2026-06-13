package com.demo.insight.preprocess.dto;

import java.util.List;

public record DataSourceListResponseDto(
        List<DataSourceTypeDto> dataTypes
) {
}

