package com.demo.insight.dataexploration.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.dataexploration.dto.HistogramFieldListResponseDto;
import com.demo.insight.dataexploration.dto.TimeseriesDataRequestDto;
import com.demo.insight.dataexploration.dto.TimeseriesDataResponseDto;
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
@RequestMapping("/api/data-exploration/timeseries")
public class DataExplorationTimeseriesController {

    private final DataExplorationService dataExplorationService;

    public DataExplorationTimeseriesController(DataExplorationService dataExplorationService) {
        this.dataExplorationService = dataExplorationService;
    }

    @GetMapping("/fields")
    public ResponseEntity<ApiResponse<HistogramFieldListResponseDto>> getTimeseriesFields(
            @RequestParam(name = "datasetKey", required = false) String datasetKey,
            @RequestParam(name = "dataset_key", required = false) String datasetKeySnakeCase,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "equipmentId", required = false) String equipmentId,
            @RequestParam(name = "equipment_id", required = false) String equipmentIdSnakeCase
    ) {
        HistogramFieldListResponseDto data = dataExplorationService.getTimeseriesFields(
                firstNonBlank(datasetKey, datasetKeySnakeCase),
                from,
                to,
                firstNonBlank(equipmentId, equipmentIdSnakeCase)
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Timeseries fields loaded."));
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<TimeseriesDataResponseDto>> getTimeseriesData(
            @Valid @RequestBody TimeseriesDataRequestDto request
    ) {
        TimeseriesDataResponseDto data = dataExplorationService.getTimeseriesData(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Timeseries data loaded."));
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
