package com.demo.insight.preprocess.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FeatureAutoScheduler {

    private final FeatureAutoService featureAutoService;

    public FeatureAutoScheduler(FeatureAutoService featureAutoService) {
        this.featureAutoService = featureAutoService;
    }

    @Scheduled(
            fixedDelayString = "${app.preprocess.feature-auto.fixed-delay-ms:300000}",
            initialDelayString = "${app.preprocess.feature-auto.initial-delay-ms:60000}"
    )
    public void runScheduledFeatureGeneration() {
        featureAutoService.runScheduledFeatureAutoJobs();
    }
}
