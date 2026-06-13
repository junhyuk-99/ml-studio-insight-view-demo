package com.demo.insight.anomalycause;

import com.demo.insight.anomalycause.dto.AnomalyCauseDetailDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseListResponseDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseRecalculateRequestDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseRecalculateRunRequestDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseRecalculateRunResultDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseRunDto;
import com.demo.insight.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/anomaly-cause")
public class AnomalyCauseController {

    private final AnomalyCauseService anomalyCauseService;

    public AnomalyCauseController(AnomalyCauseService anomalyCauseService) {
        this.anomalyCauseService = anomalyCauseService;
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<AnomalyCauseRunDto>>> getRuns() {
        List<AnomalyCauseRunDto> data = anomalyCauseService.getAnomalyCauseRuns();
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly cause run options loaded."));
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<AnomalyCauseListResponseDto>> getWindowList(
            @RequestParam("runId") String runId,
            @RequestParam("datasetKey") String datasetKey,
            @RequestParam("equipmentId") String equipmentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        AnomalyCauseListResponseDto data = anomalyCauseService.getAnomalyCauseList(
                runId,
                datasetKey,
                equipmentId,
                status,
                from,
                to,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly cause window list loaded."));
    }

    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<AnomalyCauseDetailDto>> getWindowDetail(
            @RequestParam("runId") String runId,
            @RequestParam("datasetKey") String datasetKey,
            @RequestParam("equipmentId") String equipmentId,
            @RequestParam("windowStart") String windowStart,
            @RequestParam("windowEnd") String windowEnd
    ) {
        AnomalyCauseDetailDto data = anomalyCauseService.getAnomalyCauseDetail(
                runId,
                datasetKey,
                windowStart,
                windowEnd,
                equipmentId
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly cause detail loaded."));
    }

    @PostMapping("/recalculate")
    public ResponseEntity<ApiResponse<AnomalyCauseDetailDto>> recalculateOne(
            @Valid @RequestBody AnomalyCauseRecalculateRequestDto request
    ) {
        AnomalyCauseDetailDto data = anomalyCauseService.recalculateAnomalyCause(
                request.runId(),
                request.datasetKey(),
                request.equipmentId(),
                request.windowStart(),
                request.windowEnd()
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly cause candidates recalculated."));
    }

    @PostMapping("/recalculate/run")
    public ResponseEntity<ApiResponse<AnomalyCauseRecalculateRunResultDto>> recalculateRun(
            @Valid @RequestBody AnomalyCauseRecalculateRunRequestDto request
    ) {
        AnomalyCauseRecalculateRunResultDto data = anomalyCauseService.recalculateRun(
                request.runId(),
                request.datasetKey(),
                request.equipmentId()
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Anomaly cause candidates recalculation completed."));
    }
}
