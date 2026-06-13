package com.demo.insight.dataexploration.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.dataexploration.dto.CorrelationHeatmapDataRequestDto;
import com.demo.insight.dataexploration.dto.CorrelationHeatmapDataResponseDto;
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
@RequestMapping("/api/data-exploration/correlation-heatmap")
public class DataExplorationCorrelationHeatmapController {

    private final DataExplorationService dataExplorationService;

    public DataExplorationCorrelationHeatmapController(DataExplorationService dataExplorationService) {
        this.dataExplorationService = dataExplorationService;
    }

    @GetMapping("/fields")
    public ResponseEntity<ApiResponse<HistogramFieldListResponseDto>> getCorrelationHeatmapFields(
            @RequestParam(name = "datasetKey", required = false) String datasetKey,
            @RequestParam(name = "dataset_key", required = false) String datasetKeySnakeCase,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "equipmentId", required = false) String equipmentId,
            @RequestParam(name = "equipment_id", required = false) String equipmentIdSnakeCase
    ) {
        HistogramFieldListResponseDto data = dataExplorationService.getCorrelationHeatmapFields(
                firstNonBlank(datasetKey, datasetKeySnakeCase),
                from,
                to,
                firstNonBlank(equipmentId, equipmentIdSnakeCase)
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Correlation heatmap fields loaded."));
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<CorrelationHeatmapDataResponseDto>> getCorrelationHeatmapData(
            @Valid @RequestBody CorrelationHeatmapDataRequestDto request
    ) {
        CorrelationHeatmapDataResponseDto data = dataExplorationService.getCorrelationHeatmapData(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Correlation heatmap data loaded."));
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
