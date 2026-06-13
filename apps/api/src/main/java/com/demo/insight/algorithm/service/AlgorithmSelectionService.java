package com.demo.insight.algorithm.service;

import com.demo.insight.algorithm.dto.AlgorithmSelectionResponseDto;
import com.demo.insight.algorithm.dto.AlgorithmSelectionApplyRequestDto;
import com.demo.insight.algorithm.dto.AlgorithmSelectionApplyResponseDto;

public interface AlgorithmSelectionService {
    AlgorithmSelectionResponseDto getSelectionOptions(String datasetKey);

    AlgorithmSelectionApplyResponseDto applySelection(AlgorithmSelectionApplyRequestDto request);
}
