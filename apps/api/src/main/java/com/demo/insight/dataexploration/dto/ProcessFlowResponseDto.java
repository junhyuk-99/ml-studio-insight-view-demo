package com.demo.insight.dataexploration.dto;

import java.util.List;

public record ProcessFlowResponseDto(
        String datasetKey,
        String sourceCollection,
        String mccode,
        String equipmentName,
        String opstatGroup,
        List<ProcessFlowOpstatMappingDto> opstatMapping,
        ProcessFlowRangeDto range,
        ProcessFlowLatestDto latest,
        ProcessFlowCurrentStateDto currentState,
        List<ProcessFlowTemperaturePointDto> temperatureSeries,
        List<ProcessFlowSegmentDto> processSegments,
        List<ProcessFlowStageSummaryDto> stageSummary,
        List<ProcessFlowEventDto> eventTimeline,
        List<String> warnings
) {
    public record ProcessFlowRangeDto(
            String start,
            String end,
            String timezone
    ) {
    }

    public record ProcessFlowOpstatMappingDto(
            Integer code,
            String label,
            int sortno,
            String colorKey
    ) {
    }

    public record ProcessFlowLatestDto(
            String prdtime,
            Integer opstat,
            String opstatLabel,
            String opalarm,
            String workorder,
            String patPgm,
            Integer segmentNo,
            Integer segmentTime,
            Integer segmentTotal,
            Double t1Pv,
            Double t2Pv,
            Double t1Sv,
            Double t2Sv,
            Double co2Pv,
            Double coPv,
            Double cpPv
    ) {
    }

    public record ProcessFlowCurrentStateDto(
            Integer code,
            String label,
            String startedAt,
            long elapsedSeconds,
            long rowCount
    ) {
    }

    public record ProcessFlowTemperaturePointDto(
            String prdtime,
            Integer opstat,
            String opstatLabel,
            Double t1Pv,
            Double t2Pv,
            Double t1Sv,
            Double t2Sv,
            Double co2Pv,
            Double coPv,
            Double cpPv,
            String opalarm,
            String workorder,
            String patPgm,
            Integer segmentNo,
            Integer segmentTime,
            Integer segmentTotal
    ) {
    }

    public record ProcessFlowSegmentDto(
            Integer opstat,
            String label,
            String start,
            String end,
            long durationSeconds,
            long rowCount
    ) {
    }

    public record ProcessFlowStageSummaryDto(
            Integer opstat,
            String label,
            String start,
            String end,
            long durationSeconds,
            long rowCount,
            Double avgT1Pv,
            Double avgT2Pv,
            Double avgT1Sv,
            Double avgT2Sv,
            Double minT1Pv,
            Double maxT1Pv,
            Double minT2Pv,
            Double maxT2Pv
    ) {
    }

    public record ProcessFlowEventDto(
            String time,
            String type,
            Integer fromCode,
            String fromLabel,
            Integer toCode,
            String toLabel,
            String message
    ) {
    }
}
