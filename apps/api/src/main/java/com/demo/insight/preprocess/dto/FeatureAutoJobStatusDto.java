package com.demo.insight.preprocess.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FeatureAutoJobStatusDto(
        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("dataset_label")
        String datasetLabel,

        @JsonProperty("target_collection")
        String targetCollection,

        @JsonProperty("window_size")
        int windowSize,

        @JsonProperty("selected_columns")
        List<String> selectedColumns,

        @JsonProperty("schedule_interval_seconds")
        int scheduleIntervalSeconds,

        @JsonProperty("use_yn")
        boolean useYn,

        @JsonProperty("pending_raw_count")
        long pendingRawCount,

        @JsonProperty("last_processed_timestamp")
        String lastProcessedTimestamp,

        @JsonProperty("last_processed_row_id")
        String lastProcessedRowId,

        @JsonProperty("last_window_end")
        String lastWindowEnd,

        @JsonProperty("last_run_status")
        String lastRunStatus,

        @JsonProperty("last_run_started_at")
        String lastRunStartedAt,

        @JsonProperty("last_run_finished_at")
        String lastRunFinishedAt,

        @JsonProperty("last_trigger_type")
        String lastTriggerType,

        @JsonProperty("last_total_window_count")
        int lastTotalWindowCount,

        @JsonProperty("last_created_count")
        int lastCreatedCount,

        @JsonProperty("last_skipped_count")
        int lastSkippedCount,

        @JsonProperty("last_consumed_raw_count")
        int lastConsumedRawCount,

        @JsonProperty("last_error_message")
        String lastErrorMessage,

        @JsonProperty("updated_at")
        String updatedAt
) {
}
