package com.demo.insight.modeltrain.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.modeltrain.dto.AnomalyResultListResponseDto;
import com.demo.insight.modeltrain.dto.AnomalyResultQueryResponseDto;
import com.demo.insight.modeltrain.dto.AnomalyRunListResponseDto;
import com.demo.insight.modeltrain.dto.AiOverviewResponseDto;
import com.demo.insight.modeltrain.dto.CreateModelRunRequestDto;
import com.demo.insight.modeltrain.dto.ExecuteModelRunRequestDto;
import com.demo.insight.modeltrain.dto.ExecuteModelRunResponseDto;
import com.demo.insight.modeltrain.dto.FeatureDatasetListResponseDto;
import com.demo.insight.modeltrain.dto.HomeInsightResponseDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyListResponseDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyStatusDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyUpsertRequestDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoTriggerRequestDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoTriggerResponseDto;
import com.demo.insight.modeltrain.dto.ModelRunDto;
import com.demo.insight.modeltrain.service.ModelTrainService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeltrain")
public class ModelTrainController {

    private final ModelTrainService modelTrainService;

    public ModelTrainController(ModelTrainService modelTrainService) {
        this.modelTrainService = modelTrainService;
    }

    @PostMapping("/model-runs")
    public ResponseEntity<ApiResponse<ModelRunDto>> createModelRun(
            @Valid @RequestBody CreateModelRunRequestDto request
    ) {
        ModelRunDto data = modelTrainService.createModelRun(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Model run saved as READY."));
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<ExecuteModelRunResponseDto>> executeModelRun(
            @Valid @RequestBody ExecuteModelRunRequestDto request
    ) {
        ExecuteModelRunResponseDto data = modelTrainService.executeModelRun(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Model run executed."));
    }

    @GetMapping("/auto-policies")
    public ResponseEntity<ApiResponse<ModelTrainAutoPolicyListResponseDto>> getModelTrainAutoPolicies(
            @RequestParam(name = "dataset_key", required = false) String datasetKey
    ) {
        ModelTrainAutoPolicyListResponseDto data = modelTrainService.getModelTrainAutoPolicies(datasetKey);
        return ResponseEntity.ok(ApiResponse.success(data, "Model-train auto policies loaded."));
    }

    @PostMapping("/auto-policies")
    public ResponseEntity<ApiResponse<ModelTrainAutoPolicyStatusDto>> upsertModelTrainAutoPolicy(
            @Valid @RequestBody ModelTrainAutoPolicyUpsertRequestDto request
    ) {
        ModelTrainAutoPolicyStatusDto data = modelTrainService.upsertModelTrainAutoPolicy(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Model-train auto policy saved."));
    }

    @PostMapping("/auto-policies/trigger")
    public ResponseEntity<ApiResponse<ModelTrainAutoTriggerResponseDto>> triggerModelTrainAutoPolicies(
            @RequestBody(required = false) ModelTrainAutoTriggerRequestDto request
    ) {
        ModelTrainAutoTriggerResponseDto data = modelTrainService.triggerModelTrainAutoPolicies(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Model-train auto trigger executed."));
    }

    @GetMapping("/feature-datasets")
    public ResponseEntity<ApiResponse<FeatureDatasetListResponseDto>> getFeatureDatasets() {
        FeatureDatasetListResponseDto data = modelTrainService.getFeatureDatasets();
        return ResponseEntity.ok(ApiResponse.success(data, "Feature datasets loaded."));
    }

    @GetMapping("/anomaly-results")
    public ResponseEntity<ApiResponse<AnomalyResultListResponseDto>> getAnomalyResults(
            @RequestParam("run_id") String runId,
            @RequestParam(name = "dataset_key", required = false) String datasetKey,
            @RequestParam(name = "equipment_id", required = false) String equipmentId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        AnomalyResultListResponseDto data = modelTrainService.getAnomalyResults(runId, datasetKey, equipmentId, limit);
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly results loaded."));
    }

    @GetMapping("/anomaly/runs")
    public ResponseEntity<ApiResponse<AnomalyRunListResponseDto>> getAnomalyRunOptions(
            @RequestParam(name = "algo_code", required = false) String algoCode,
            @RequestParam(name = "dataset_key", required = false) String datasetKey,
            @RequestParam(name = "equipment_id", required = false) String equipmentId,
            @RequestParam(name = "include_non_success", required = false) Boolean includeNonSuccess,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        AnomalyRunListResponseDto data = modelTrainService.getAnomalyRunOptions(
                algoCode,
                datasetKey,
                equipmentId,
                includeNonSuccess,
                limit
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly run options loaded."));
    }

    @GetMapping("/anomaly/results")
    public ResponseEntity<ApiResponse<AnomalyResultQueryResponseDto>> getAnomalyResultView(
            @RequestParam(name = "algo_code", required = false) String algoCode,
            @RequestParam(name = "run_id", required = false) String runId,
            @RequestParam(name = "dataset_key", required = false) String datasetKey,
            @RequestParam(name = "equipment_id", required = false) String equipmentId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        AnomalyResultQueryResponseDto data = modelTrainService.getAnomalyResultView(
                algoCode,
                runId,
                datasetKey,
                equipmentId,
                limit
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly result view loaded."));
    }

    @GetMapping("/anomaly/runs/{runId}")
    public ResponseEntity<ApiResponse<AnomalyResultQueryResponseDto>> getAnomalyResultDetailByRunId(
            @PathVariable("runId") String runId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        AnomalyResultQueryResponseDto data = modelTrainService.getAnomalyResultDetailByRunId(runId, limit);
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly run detail loaded."));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AiOverviewResponseDto>> getAiOverview() {
        AiOverviewResponseDto data = modelTrainService.getAiOverview();
        return ResponseEntity.ok(ApiResponse.success(data, "AI operation overview loaded."));
    }

    @GetMapping("/overview/home-insight")
    public ResponseEntity<ApiResponse<HomeInsightResponseDto>> getHomeInsight(
            @RequestParam(name = "run_id", required = false) String runId
    ) {
        HomeInsightResponseDto data = modelTrainService.getHomeInsight(runId);
        return ResponseEntity.ok(ApiResponse.success(data, "Home insight loaded."));
    }
}
