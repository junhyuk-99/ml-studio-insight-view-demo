package com.demo.insight.dataexploration.dto;

public record ProcessFlowQueryDto(
        String datasetKey,
        String mccode,
        String start,
        String end,
        String opstats,
        String fields,
        Integer limit,
        Boolean autoRefresh
) {
}
