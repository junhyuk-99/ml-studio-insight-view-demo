package com.demo.insight.healthindex;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.healthindex.dto.HealthIndexRunDto;
import com.demo.insight.healthindex.dto.HealthIndexTrendResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/health-index")
public class HealthIndexController {

    private final HealthIndexService healthIndexService;

    public HealthIndexController(HealthIndexService healthIndexService) {
        this.healthIndexService = healthIndexService;
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<HealthIndexRunDto>>> getRuns() {
        List<HealthIndexRunDto> data = healthIndexService.getHealthIndexRuns();
        return ResponseEntity.ok(ApiResponse.success(data, "Health index run options loaded."));
    }

    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<HealthIndexTrendResponseDto>> getTrend(
            @RequestParam("runId") String runId,
            @RequestParam("datasetKey") String datasetKey,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        HealthIndexTrendResponseDto data = healthIndexService.getHealthIndexTrend(
                runId,
                datasetKey,
                status,
                from,
                to
        );
        return ResponseEntity.ok(ApiResponse.success(data, "Health index trend loaded."));
    }
}
