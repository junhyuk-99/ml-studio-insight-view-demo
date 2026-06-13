package com.demo.insight.supervisedresult;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.supervisedresult.dto.SupervisedDistributionResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedErrorsResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedPredictionPageResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedRunDto;
import com.demo.insight.supervisedresult.dto.SupervisedSummaryResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedTrendPointDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supervised/result")
public class SupervisedResultController {

    private final SupervisedResultService supervisedResultService;

    public SupervisedResultController(SupervisedResultService supervisedResultService) {
        this.supervisedResultService = supervisedResultService;
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<SupervisedRunDto>>> getRuns(
            @RequestParam(name = "triggerType", required = false) String triggerType,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        List<SupervisedRunDto> data = supervisedResultService.getRuns(triggerType, limit);
        return ResponseEntity.ok(ApiResponse.success(data, "Supervised run list loaded."));
    }

    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<List<SupervisedTrendPointDto>>> getTrend(
            @RequestParam(name = "triggerType", required = false) String triggerType,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        List<SupervisedTrendPointDto> data = supervisedResultService.getTrend(triggerType, limit);
        return ResponseEntity.ok(ApiResponse.success(data, "Supervised trend loaded."));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SupervisedSummaryResponseDto>> getSummary(
            @RequestParam("runId") String runId
    ) {
        SupervisedSummaryResponseDto data = supervisedResultService.getSummary(runId);
        return ResponseEntity.ok(ApiResponse.success(data, "Supervised summary loaded."));
    }

    @GetMapping("/distribution")
    public ResponseEntity<ApiResponse<SupervisedDistributionResponseDto>> getDistribution(
            @RequestParam("runId") String runId
    ) {
        SupervisedDistributionResponseDto data = supervisedResultService.getDistribution(runId);
        return ResponseEntity.ok(ApiResponse.success(data, "Supervised distribution loaded."));
    }

    @GetMapping("/errors")
    public ResponseEntity<ApiResponse<SupervisedErrorsResponseDto>> getErrors(
            @RequestParam("runId") String runId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        SupervisedErrorsResponseDto data = supervisedResultService.getErrors(runId, limit);
        return ResponseEntity.ok(ApiResponse.success(data, "Supervised error rows loaded."));
    }

    @GetMapping("/predictions")
    public ResponseEntity<ApiResponse<SupervisedPredictionPageResponseDto>> getPredictions(
            @RequestParam("runId") String runId,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        SupervisedPredictionPageResponseDto data = supervisedResultService.getPredictions(
                runId,
                filter,
                from,
                to,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Supervised prediction grid loaded."));
    }
}
