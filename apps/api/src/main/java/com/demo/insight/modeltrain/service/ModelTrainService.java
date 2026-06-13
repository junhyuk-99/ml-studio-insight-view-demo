package com.demo.insight.modeltrain.service;

import com.demo.insight.modeltrain.dto.AnomalyResultListResponseDto;
import com.demo.insight.modeltrain.dto.AnomalyResultQueryResponseDto;
import com.demo.insight.modeltrain.dto.AnomalyRunListResponseDto;
import com.demo.insight.modeltrain.dto.AiOverviewResponseDto;
import com.demo.insight.modeltrain.dto.CreateModelRunRequestDto;
import com.demo.insight.modeltrain.dto.ExecuteModelRunRequestDto;
import com.demo.insight.modeltrain.dto.ExecuteModelRunResponseDto;
import com.demo.insight.modeltrain.dto.HomeInsightResponseDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyListResponseDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyStatusDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoPolicyUpsertRequestDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoTriggerRequestDto;
import com.demo.insight.modeltrain.dto.ModelTrainAutoTriggerResponseDto;
import com.demo.insight.modeltrain.dto.FeatureDatasetListResponseDto;
import com.demo.insight.modeltrain.dto.ModelRunDto;

public interface ModelTrainService {
    ModelRunDto createModelRun(CreateModelRunRequestDto request);

    ExecuteModelRunResponseDto executeModelRun(ExecuteModelRunRequestDto request);

    ModelTrainAutoPolicyListResponseDto getModelTrainAutoPolicies(String datasetKey);

    ModelTrainAutoPolicyStatusDto upsertModelTrainAutoPolicy(ModelTrainAutoPolicyUpsertRequestDto request);

    ModelTrainAutoTriggerResponseDto triggerModelTrainAutoPolicies(ModelTrainAutoTriggerRequestDto request);

    void runScheduledModelTrainAutoPolicies();

    AnomalyResultListResponseDto getAnomalyResults(String runId, String datasetKey, String equipmentId, Integer limit);

    AnomalyRunListResponseDto getAnomalyRunOptions(
            String algoCode,
            String datasetKey,
            String equipmentId,
            Boolean includeNonSuccess,
            Integer limit
    );

    AnomalyResultQueryResponseDto getAnomalyResultView(
            String algoCode,
            String runId,
            String datasetKey,
            String equipmentId,
            Integer limit
    );

    AnomalyResultQueryResponseDto getAnomalyResultDetailByRunId(String runId, Integer limit);

    AiOverviewResponseDto getAiOverview();

    HomeInsightResponseDto getHomeInsight(String runId);

    FeatureDatasetListResponseDto getFeatureDatasets();
}
