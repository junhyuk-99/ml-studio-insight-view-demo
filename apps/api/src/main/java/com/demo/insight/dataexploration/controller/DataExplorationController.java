package com.demo.insight.dataexploration.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.dataexploration.dto.HistogramDataRequestDto;
import com.demo.insight.dataexploration.dto.HistogramDataResponseDto;
import com.demo.insight.dataexploration.dto.HistogramFieldListResponseDto;
import com.demo.insight.dataexploration.service.DataExplorationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-exploration/histogram")
public class DataExplorationController {

    private final DataExplorationService dataExplorationService;

    public DataExplorationController(DataExplorationService dataExplorationService) {
        this.dataExplorationService = dataExplorationService;
    }

    @GetMapping("/fields")
    public ResponseEntity<ApiResponse<HistogramFieldListResponseDto>> getHistogramFields(
            @RequestParam(name = "datasetKey", required = false) String datasetKey,
            @RequestParam(name = "dataset_key", required = false) String datasetKeySnakeCase,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "equipmentId", required = false) String equipmentId,
            @RequestParam(name = "equipment_id", required = false) String equipmentIdSnakeCase
    ) {
        HistogramFieldListResponseDto data = dataExplorationService.getHistogramFields(
                firstNonBlank(datasetKey, datasetKeySnakeCase),
                from,
                to,
                firstNonBlank(equipmentId, equipmentIdSnakeCase)
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Histogram fields loaded."));
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<HistogramDataResponseDto>> getHistogramData(
            @Valid @RequestBody HistogramDataRequestDto request
    ) {
        HistogramDataResponseDto data = dataExplorationService.getHistogramData(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Histogram data loaded."));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
