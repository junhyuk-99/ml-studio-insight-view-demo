package com.demo.insight.healthindex;

import com.demo.insight.healthindex.dto.HealthIndexRunDto;
import com.demo.insight.healthindex.dto.HealthIndexTrendResponseDto;

import java.util.List;

public interface HealthIndexService {

    List<HealthIndexRunDto> getHealthIndexRuns();

    HealthIndexTrendResponseDto getHealthIndexTrend(
            String runId,
            String datasetKey,
            String status,
            String from,
            String to
    );
}
