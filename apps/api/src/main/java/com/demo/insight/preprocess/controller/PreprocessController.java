package com.demo.insight.preprocess.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.preprocess.dto.DataSourceListResponseDto;
import com.demo.insight.preprocess.dto.FeatureAutoJobListResponseDto;
import com.demo.insight.preprocess.dto.FeatureAutoJobStatusDto;
import com.demo.insight.preprocess.dto.FeatureAutoJobUpsertRequestDto;
import com.demo.insight.preprocess.dto.FeatureGenerationRequestDto;
import com.demo.insight.preprocess.dto.FeatureGenerationResponseDto;
import com.demo.insight.preprocess.dto.FeaturePreviewResponseDto;
import com.demo.insight.preprocess.dto.FeatureAutoTriggerRequestDto;
import com.demo.insight.preprocess.dto.FeatureAutoTriggerResponseDto;
import com.demo.insight.preprocess.dto.PreprocessOptionResponseDto;
import com.demo.insight.preprocess.dto.RawDataPreviewResponseDto;
import com.demo.insight.preprocess.service.FeatureAutoService;
import com.demo.insight.preprocess.service.PreprocessService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preprocess")
public class PreprocessController {

    private final PreprocessService preprocessService;
    private final FeatureAutoService featureAutoService;

    public PreprocessController(
            PreprocessService preprocessService,
            FeatureAutoService featureAutoService
    ) {
        this.preprocessService = preprocessService;
        this.featureAutoService = featureAutoService;
    }

    @GetMapping("/data-sources")
    public ResponseEntity<ApiResponse<DataSourceListResponseDto>> getDataSources() {
        DataSourceListResponseDto data = preprocessService.getDataSources();
        return ResponseEntity.ok(ApiResponse.success(data, "Preprocess data sources loaded."));
    }

    @GetMapping("/raw-preview")
    public ResponseEntity<ApiResponse<RawDataPreviewResponseDto>> getRawPreview(
            @RequestParam(name = "datasetKey", required = false) String datasetKey,
            @RequestParam(name = "typeCode", required = false) String typeCode,
            @RequestParam(name = "dtlCode", required = false) String dtlCode,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "equipmentId", required = false) String equipmentId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        RawDataPreviewResponseDto data = preprocessService.getRawDataPreview(
                datasetKey,
                typeCode,
                dtlCode,
                from,
                to,
                equipmentId,
                limit
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Raw preview loaded."));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<PreprocessOptionResponseDto>> getPreprocessOptions() {
        PreprocessOptionResponseDto data = preprocessService.getPreprocessOptions();
        return ResponseEntity.ok(ApiResponse.success(data, "Preprocess options loaded."));
    }

    @PostMapping("/features/generate")
    public ResponseEntity<ApiResponse<FeatureGenerationResponseDto>> generateFeatures(
            @Valid @RequestBody FeatureGenerationRequestDto request
    ) {
        FeatureGenerationResponseDto data = preprocessService.generateFeatures(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Feature generation completed."));
    }

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<FeaturePreviewResponseDto>> getFeaturePreview(
            @RequestParam(value = "dataset_key", required = false) String datasetKey,
            @RequestParam(value = "equipment_id", required = false) String equipmentId,
            @RequestParam(value = "sensor_id", required = false) String sensorId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "compact", required = false) Boolean compact
    ) {
        FeaturePreviewResponseDto data = preprocessService.getFeaturePreview(
                datasetKey,
                equipmentId,
                sensorId,
                limit,
                compact
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Feature preview loaded."));
    }

    @GetMapping("/feature-auto/jobs")
    public ResponseEntity<ApiResponse<FeatureAutoJobListResponseDto>> getFeatureAutoJobs() {
        FeatureAutoJobListResponseDto data = featureAutoService.getFeatureAutoJobs();
        return ResponseEntity.ok(ApiResponse.success(data, "Feature auto jobs loaded."));
    }

    @PostMapping("/feature-auto/jobs")
    public ResponseEntity<ApiResponse<FeatureAutoJobStatusDto>> upsertFeatureAutoJob(
            @Valid @RequestBody FeatureAutoJobUpsertRequestDto request
    ) {
        FeatureAutoJobStatusDto data = featureAutoService.upsertFeatureAutoJob(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Feature auto job saved."));
    }

    @PostMapping("/feature-auto/jobs/trigger")
    public ResponseEntity<ApiResponse<FeatureAutoTriggerResponseDto>> triggerFeatureAutoJobs(
            @RequestBody(required = false) FeatureAutoTriggerRequestDto request
    ) {
        FeatureAutoTriggerResponseDto data = featureAutoService.triggerFeatureAutoJobs(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Feature auto trigger executed."));
    }
}
