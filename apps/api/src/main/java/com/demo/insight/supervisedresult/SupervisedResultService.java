package com.demo.insight.supervisedresult;

import com.demo.insight.supervisedresult.dto.SupervisedDistributionResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedErrorsResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedPredictionPageResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedRunDto;
import com.demo.insight.supervisedresult.dto.SupervisedSummaryResponseDto;
import com.demo.insight.supervisedresult.dto.SupervisedTrendPointDto;

import java.util.List;

public interface SupervisedResultService {

    List<SupervisedRunDto> getRuns(String triggerType, Integer limit);

    List<SupervisedTrendPointDto> getTrend(String triggerType, Integer limit);

    SupervisedSummaryResponseDto getSummary(String runId);

    SupervisedDistributionResponseDto getDistribution(String runId);

    SupervisedErrorsResponseDto getErrors(String runId, Integer limit);

    SupervisedPredictionPageResponseDto getPredictions(
            String runId,
            String filter,
            String from,
            String to,
            Integer page,
            Integer size
    );
}
