package com.demo.insight.dataexploration.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.dataexploration.dto.DataExplorationDatasetOptionDto;
import com.demo.insight.dataexploration.service.DataExplorationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/data-exploration")
public class DataExplorationDatasetController {

    private final DataExplorationService dataExplorationService;

    public DataExplorationDatasetController(DataExplorationService dataExplorationService) {
        this.dataExplorationService = dataExplorationService;
    }

    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<DataExplorationDatasetOptionDto>>> getDatasets() {
        List<DataExplorationDatasetOptionDto> data = dataExplorationService.getDatasets();
        return ResponseEntity.ok(ApiResponse.success(data, "Data exploration datasets loaded."));
    }
}
