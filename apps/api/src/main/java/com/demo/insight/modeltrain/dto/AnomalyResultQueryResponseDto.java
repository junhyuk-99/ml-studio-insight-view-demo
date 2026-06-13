package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnomalyResultQueryResponseDto(
        AnomalyRunDetailDto run,
        AnomalyResultSummaryDto summary,

        @JsonProperty("anomaly_results")
        List<AnomalyResultPointDto> anomalyResults,

        @JsonProperty("selected_dataset_key")
        String selectedDatasetKey,

        @JsonProperty("selected_equipment_id")
        String selectedEquipmentId,

        @JsonProperty("selected_run_id")
        String selectedRunId
) {
    public AnomalyResultQueryResponseDto(
            AnomalyRunDetailDto run,
            AnomalyResultSummaryDto summary,
            List<AnomalyResultPointDto> anomalyResults
    ) {
        this(
                run,
                summary,
                anomalyResults,
                run == null ? null : run.datasetKey(),
                run == null ? null : run.equipmentId(),
                run == null ? null : run.runId()
        );
    }
}
