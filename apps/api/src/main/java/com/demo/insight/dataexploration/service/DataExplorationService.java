package com.demo.insight.dataexploration.service;

import com.demo.insight.dataexploration.dto.HistogramDataRequestDto;
import com.demo.insight.dataexploration.dto.HistogramDataResponseDto;
import com.demo.insight.dataexploration.dto.HistogramFieldListResponseDto;
import com.demo.insight.dataexploration.dto.BoxplotDataRequestDto;
import com.demo.insight.dataexploration.dto.BoxplotDataResponseDto;
import com.demo.insight.dataexploration.dto.CorrelationHeatmapDataRequestDto;
import com.demo.insight.dataexploration.dto.CorrelationHeatmapDataResponseDto;
import com.demo.insight.dataexploration.dto.TimeseriesDataRequestDto;
import com.demo.insight.dataexploration.dto.TimeseriesDataResponseDto;
import com.demo.insight.dataexploration.dto.DataExplorationDatasetOptionDto;

import java.util.List;

public interface DataExplorationService {
    List<DataExplorationDatasetOptionDto> getDatasets();

    HistogramFieldListResponseDto getHistogramFields(String datasetKey, String from, String to, String equipmentId);

    HistogramDataResponseDto getHistogramData(HistogramDataRequestDto request);

    HistogramFieldListResponseDto getTimeseriesFields(String datasetKey, String from, String to, String equipmentId);

    TimeseriesDataResponseDto getTimeseriesData(TimeseriesDataRequestDto request);

    HistogramFieldListResponseDto getCorrelationHeatmapFields(String datasetKey, String from, String to, String equipmentId);

    CorrelationHeatmapDataResponseDto getCorrelationHeatmapData(CorrelationHeatmapDataRequestDto request);

    HistogramFieldListResponseDto getBoxplotFields(String datasetKey, String from, String to, String equipmentId);

    BoxplotDataResponseDto getBoxplotData(BoxplotDataRequestDto request);
}
