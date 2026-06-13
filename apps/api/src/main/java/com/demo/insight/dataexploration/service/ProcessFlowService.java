package com.demo.insight.dataexploration.service;

import com.demo.insight.dataexploration.dto.ProcessFlowQueryDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto;

public interface ProcessFlowService {
    ProcessFlowResponseDto getProcessFlow(ProcessFlowQueryDto query);
}
