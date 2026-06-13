package com.demo.insight.algorithm.controller;

import com.demo.insight.algorithm.dto.AlgorithmSelectionApplyRequestDto;
import com.demo.insight.algorithm.dto.AlgorithmSelectionApplyResponseDto;
import com.demo.insight.algorithm.dto.AlgorithmSelectionResponseDto;
import com.demo.insight.algorithm.service.AlgorithmSelectionService;
import com.demo.insight.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/algorithms")
public class AlgorithmSelectionController {

    private final AlgorithmSelectionService algorithmSelectionService;

    public AlgorithmSelectionController(AlgorithmSelectionService algorithmSelectionService) {
        this.algorithmSelectionService = algorithmSelectionService;
    }

    @GetMapping("/selection")
    public ResponseEntity<ApiResponse<AlgorithmSelectionResponseDto>> getSelectionOptions(
            @RequestParam(name = "dataset_key", required = false) String datasetKey
    ) {
        AlgorithmSelectionResponseDto data = algorithmSelectionService.getSelectionOptions(datasetKey);
        return ResponseEntity.ok(ApiResponse.success(data, "Algorithm selection options loaded."));
    }

    @PostMapping("/selection/apply")
    public ResponseEntity<ApiResponse<AlgorithmSelectionApplyResponseDto>> applySelection(
            @Valid @RequestBody AlgorithmSelectionApplyRequestDto request
    ) {
        AlgorithmSelectionApplyResponseDto data = algorithmSelectionService.applySelection(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Algorithm selection applied."));
    }
}
