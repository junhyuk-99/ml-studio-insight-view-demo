package com.demo.insight.modeltrain.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ModelTrainAutoScheduler {

    private final ModelTrainService modelTrainService;

    public ModelTrainAutoScheduler(ModelTrainService modelTrainService) {
        this.modelTrainService = modelTrainService;
    }

    @Scheduled(
            fixedDelayString = "${app.modeltrain.auto.fixed-delay-ms:300000}",
            initialDelayString = "${app.modeltrain.auto.initial-delay-ms:60000}"
    )
    public void runScheduledModelTrainAutoPolicies() {
        modelTrainService.runScheduledModelTrainAutoPolicies();
    }
}

