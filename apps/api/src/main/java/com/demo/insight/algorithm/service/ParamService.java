package com.demo.insight.algorithm.service;

import com.demo.insight.algorithm.dto.AlgorithmParamDto;
import com.demo.insight.algorithm.dto.AlgorithmParamSaveRequestDto;
import com.demo.insight.algorithm.dto.AlgorithmParamSaveResponseDto;

import java.util.List;

public interface ParamService {
    List<AlgorithmParamDto> getParamsByAlgoCd(String algoCd);

    AlgorithmParamSaveResponseDto saveParams(AlgorithmParamSaveRequestDto request);
}
