package com.demo.insight.thresholdalert;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.thresholdalert.dto.ThresholdAlertAckRequestDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertListItemDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertListResponseDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertRecalculateRunRequestDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertRecalculateRunResultDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertSummaryDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertTrendResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/threshold-alert")
public class ThresholdAlertController {

    private final ThresholdAlertService thresholdAlertService;

    public ThresholdAlertController(ThresholdAlertService thresholdAlertService) {
        this.thresholdAlertService = thresholdAlertService;
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<ThresholdAlertListResponseDto>> getAlertList(
            @RequestParam("datasetKey") String datasetKey,
            @RequestParam(name = "runId", required = false) String runId,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "ackYn", required = false) String ackYn,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        ThresholdAlertListResponseDto data = thresholdAlertService.getThresholdAlertList(
                datasetKey,
                runId,
                severity,
                status,
                ackYn,
                from,
                to,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Threshold alert list loaded."));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ThresholdAlertSummaryDto>> getAlertSummary(
            @RequestParam("datasetKey") String datasetKey,
            @RequestParam(name = "runId", required = false) String runId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        ThresholdAlertSummaryDto data = thresholdAlertService.getThresholdAlertSummary(
                datasetKey,
                runId,
                from,
                to
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Threshold alert summary loaded."));
    }

    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<ThresholdAlertTrendResponseDto>> getAlertTrend(
            @RequestParam("datasetKey") String datasetKey,
            @RequestParam(name = "runId", required = false) String runId,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "ackYn", required = false) String ackYn,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        ThresholdAlertTrendResponseDto data = thresholdAlertService.getThresholdAlertTrend(
                datasetKey,
                runId,
                severity,
                status,
                ackYn,
                from,
                to,
                limit
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Threshold alert trend loaded."));
    }

    @PostMapping("/recalculate/run")
    public ResponseEntity<ApiResponse<ThresholdAlertRecalculateRunResultDto>> recalculateRun(
            @Valid @RequestBody ThresholdAlertRecalculateRunRequestDto request
    ) {
        ThresholdAlertRecalculateRunResultDto data = thresholdAlertService.recalculateRun(
                request.runId(),
                request.datasetKey()
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Threshold alert recalculation completed."));
    }

    @PostMapping("/ack")
    public ResponseEntity<ApiResponse<ThresholdAlertListItemDto>> ackAlert(
            @Valid @RequestBody ThresholdAlertAckRequestDto request
    ) {
        ThresholdAlertListItemDto data = thresholdAlertService.ackAlert(
                request.alertId(),
                request.ackBy(),
                request.memo()
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Threshold alert acknowledged."));
    }
}
