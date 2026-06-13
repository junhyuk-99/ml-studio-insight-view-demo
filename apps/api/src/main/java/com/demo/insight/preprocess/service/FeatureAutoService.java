package com.demo.insight.preprocess.service;

import com.demo.insight.preprocess.dto.FeatureAutoJobListResponseDto;
import com.demo.insight.preprocess.dto.FeatureAutoJobStatusDto;
import com.demo.insight.preprocess.dto.FeatureAutoJobUpsertRequestDto;
import com.demo.insight.preprocess.dto.FeatureAutoTriggerRequestDto;
import com.demo.insight.preprocess.dto.FeatureAutoTriggerResponseDto;

public interface FeatureAutoService {

    FeatureAutoJobListResponseDto getFeatureAutoJobs();

    FeatureAutoJobStatusDto upsertFeatureAutoJob(FeatureAutoJobUpsertRequestDto request);

    FeatureAutoTriggerResponseDto triggerFeatureAutoJobs(FeatureAutoTriggerRequestDto request);

    void runScheduledFeatureAutoJobs();
}
