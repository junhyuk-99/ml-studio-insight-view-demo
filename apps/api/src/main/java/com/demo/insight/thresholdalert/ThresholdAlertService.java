package com.demo.insight.thresholdalert;

import com.demo.insight.thresholdalert.dto.ThresholdAlertBackfillResultDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertListItemDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertListResponseDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertRecalculateRunResultDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertSummaryDto;
import com.demo.insight.thresholdalert.dto.ThresholdAlertTrendResponseDto;

public interface ThresholdAlertService {

    ThresholdAlertListResponseDto getThresholdAlertList(
            String datasetKey,
            String runId,
            String severity,
            String status,
            String ackYn,
            String from,
            String to,
            Integer page,
            Integer size
    );

    ThresholdAlertSummaryDto getThresholdAlertSummary(
            String datasetKey,
            String runId,
            String from,
            String to
    );

    ThresholdAlertTrendResponseDto getThresholdAlertTrend(
            String datasetKey,
            String runId,
            String severity,
            String status,
            String ackYn,
            String from,
            String to,
            Integer limit
    );

    ThresholdAlertRecalculateRunResultDto recalculateRun(
            String runId,
            String datasetKey
    );

    ThresholdAlertListItemDto ackAlert(
            String alertId,
            String ackBy,
            String memo
    );

    ThresholdAlertBackfillResultDto backfillMissingAlerts(Integer limit);
}
