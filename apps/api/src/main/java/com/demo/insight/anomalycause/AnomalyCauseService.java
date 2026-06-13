package com.demo.insight.anomalycause;

import com.demo.insight.anomalycause.dto.AnomalyCauseDetailDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseBackfillResultDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseListResponseDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseRecalculateRunResultDto;
import com.demo.insight.anomalycause.dto.AnomalyCauseRunDto;

import java.util.List;

public interface AnomalyCauseService {
    List<AnomalyCauseRunDto> getAnomalyCauseRuns();

    AnomalyCauseListResponseDto getAnomalyCauseList(
            String runId,
            String datasetKey,
            String equipmentId,
            String status,
            String from,
            String to,
            Integer page,
            Integer size
    );

    AnomalyCauseDetailDto getAnomalyCauseDetail(
            String runId,
            String datasetKey,
            String windowStart,
            String windowEnd,
            String equipmentId
    );

    AnomalyCauseDetailDto recalculateAnomalyCause(
            String runId,
            String datasetKey,
            String equipmentId,
            String windowStart,
            String windowEnd
    );

    AnomalyCauseRecalculateRunResultDto recalculateRun(
            String runId,
            String datasetKey,
            String equipmentId
    );

    AnomalyCauseBackfillResultDto backfillMissingCauses(Integer limit);
}
