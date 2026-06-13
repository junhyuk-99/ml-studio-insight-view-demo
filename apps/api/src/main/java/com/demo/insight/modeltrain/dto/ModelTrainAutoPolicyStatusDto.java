package com.demo.insight.modeltrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ModelTrainAutoPolicyStatusDto(
        @JsonProperty("policy_id")
        String policyId,

        @JsonProperty("dataset_key")
        String datasetKey,

        @JsonProperty("dataset_label")
        String datasetLabel,

        @JsonProperty("dataset_name")
        String datasetName,

        @JsonProperty("equipment_id")
        String equipmentId,

        @JsonProperty("source_collection")
        String sourceCollection,

        @JsonProperty("target_collection")
        String targetCollection,

        @JsonProperty("selected_columns")
        List<String> selectedColumns,

        @JsonProperty("window_size")
        int windowSize,

        @JsonProperty("window_mode")
        String windowMode,

        @JsonProperty("feature_stats")
        List<String> featureStats,

        @JsonProperty("scheduler_enabled")
        Boolean schedulerEnabled,

        @JsonProperty("scheduler_interval_sec")
        int schedulerIntervalSec,

        @JsonProperty("last_status")
        String lastStatus,

        @JsonProperty("last_window_end")
        String lastWindowEnd,

        @JsonProperty("last_checkpoint_value")
        String lastCheckpointValue,

        @JsonProperty("feature_config_source")
        String featureConfigSource,

        @JsonProperty("feature_config_message")
        String featureConfigMessage,

        @JsonProperty("algo_code")
        String algoCode,

        @JsonProperty("algo_name")
        String algoName,

        Map<String, Object> params,

        @JsonProperty("auto_train_enabled")
        boolean autoTrainEnabled,

        @JsonProperty("model_scheduler_interval_sec")
        int modelSchedulerIntervalSec,

        @JsonProperty("model_scheduler_interval_minutes")
        Integer modelSchedulerIntervalMinutes,

        @JsonProperty("min_new_feature_count")
        int minNewFeatureCount,

        @JsonProperty("min_total_feature_count")
        int minTotalFeatureCount,

        @JsonProperty("recent_window_limit")
        Integer recentWindowLimit,

        @JsonProperty("pending_new_feature_count")
        long pendingNewFeatureCount,

        @JsonProperty("total_feature_count")
        long totalFeatureCount,

        @JsonProperty("last_train_at")
        String lastTrainAt,

        @JsonProperty("last_train_status")
        String lastTrainStatus,

        @JsonProperty("last_run_id")
        String lastRunId,

        @JsonProperty("last_train_window_end")
        String lastTrainWindowEnd,

        @JsonProperty("last_skip_reason")
        String lastSkipReason,

        @JsonProperty("last_error_message")
        String lastErrorMessage,

        @JsonProperty("last_checked_at")
        String lastCheckedAt,

        @JsonProperty("last_run_started_at")
        String lastRunStartedAt,

        @JsonProperty("last_run_finished_at")
        String lastRunFinishedAt,

        @JsonProperty("run_history_count")
        long runHistoryCount,

        @JsonProperty("updated_at")
        String updatedAt,

        @JsonProperty("created_at")
        String createdAt
) {
}
