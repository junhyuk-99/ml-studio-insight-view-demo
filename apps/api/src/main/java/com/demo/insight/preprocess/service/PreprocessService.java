package com.demo.insight.preprocess.service;

import com.demo.insight.preprocess.dto.DataSourceListResponseDto;
import com.demo.insight.preprocess.dto.FeatureGenerationRequestDto;
import com.demo.insight.preprocess.dto.FeatureGenerationResponseDto;
import com.demo.insight.preprocess.dto.FeaturePreviewResponseDto;
import com.demo.insight.preprocess.dto.PreprocessOptionResponseDto;
import com.demo.insight.preprocess.dto.RawDataPreviewResponseDto;

public interface PreprocessService {
    DataSourceListResponseDto getDataSources();

    RawDataPreviewResponseDto getRawDataPreview(
            String datasetKey,
            String typeCode,
            String dtlCode,
            String from,
            String to,
            String equipmentId,
            Integer limit
    );

    PreprocessOptionResponseDto getPreprocessOptions();

    FeatureGenerationResponseDto generateFeatures(FeatureGenerationRequestDto request);

    FeaturePreviewResponseDto getFeaturePreview(
            String datasetKeyJson,
            String equipmentId,
            String sensorId,
            Integer limit,
            Boolean compact
    );
}
