package com.demo.insight.dataexploration.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.dataexploration.dto.ProcessFlowQueryDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto;
import com.demo.insight.dataexploration.service.ProcessFlowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataExplorationProcessFlowController {

    private final ProcessFlowService processFlowService;

    public DataExplorationProcessFlowController(ProcessFlowService processFlowService) {
        this.processFlowService = processFlowService;
    }

    @GetMapping({"/api/data-exploration/processflow", "/api/dataexploration/processflow"})
    public ResponseEntity<ApiResponse<ProcessFlowResponseDto>> getProcessFlow(
            @RequestParam(name = "datasetKey", required = false) String datasetKey,
            @RequestParam(name = "dataset_key", required = false) String datasetKeySnakeCase,
            @RequestParam(name = "mccode", required = false) String mccode,
            @RequestParam(name = "MCCODE", required = false) String mccodeUpperCase,
            @RequestParam(name = "equipmentId", required = false) String equipmentId,
            @RequestParam(name = "equipment_id", required = false) String equipmentIdSnakeCase,
            @RequestParam(name = "start", required = false) String start,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "end", required = false) String end,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "opstats", required = false) String opstats,
            @RequestParam(name = "fields", required = false) String fields,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "autoRefresh", required = false) Boolean autoRefresh,
            @RequestParam(name = "auto_refresh", required = false) Boolean autoRefreshSnakeCase
    ) {
        ProcessFlowQueryDto query = new ProcessFlowQueryDto(
                firstNonBlank(datasetKey, datasetKeySnakeCase),
                firstNonBlank(mccode, mccodeUpperCase, equipmentId, equipmentIdSnakeCase),
                firstNonBlank(start, from),
                firstNonBlank(end, to),
                opstats,
                fields,
                limit,
                autoRefresh != null ? autoRefresh : autoRefreshSnakeCase
        );
        ProcessFlowResponseDto data = processFlowService.getProcessFlow(query);
        return ResponseEntity.ok(ApiResponse.success(data, "Process flow data loaded."));
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
