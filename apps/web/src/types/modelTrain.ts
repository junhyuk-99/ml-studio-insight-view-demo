import type { AlgorithmParamItem } from './algorithmParam';

export type ModelTrainNavigationState = {
  algoCd?: string;
  algoNm?: string;
  dataset_key?: string;
  equipment_id?: string;
  selected_columns?: string[];
  window_size?: number;
};

export type FeatureDataset = {
  dataset_key: string;
  dataset_label: string;
  dataset_name: string | null;
  equipment_id: string | null;
  source_collection: string | null;
  target_collection: string | null;
  selected_columns: string[];
  window_size: number | null;
  window_mode: string | null;
  feature_stats: string[];
  scheduler_enabled: boolean | null;
  scheduler_interval_sec: number | null;
  last_status: string | null;
  last_window_end: string | null;
  last_checkpoint_value: string | null;
  config_source: string | null;
  config_message: string | null;
  SOURCE_TYPE_CODE: string | null;
  SOURCE_DTL_CODE: string | null;
  SOURCE_FILE: string | null;
};

export type FeatureDatasetListData = {
  feature_datasets: FeatureDataset[];
};

export type ModelRunSaveRequest = {
  dataset_key: string;
  equipment_id?: string;
  selected_columns: string[];
  window_size: number;
  algo_code: string;
  algo_name: string;
  params: Record<string, unknown>;
};

export type ModelRunData = {
  run_id: string;
  equipment_id: string;
  sensor_id: string;
  dataset_key: string;
  selected_columns: string[];
  window_size: number;
  algo_code: string;
  algo_name: string;
  params: Record<string, unknown>;
  status: string;
  reg_date: string;
};

export type ExecuteModelRunRequest = {
  run_id: string;
};

export type ExecuteModelRunData = {
  run_id: string;
  status: string;
  processed_window_count: number;
  saved_result_count: number;
  meta_only_param_keys: string[];
};

export type ModelTrainAutoPolicyUpsertRequest = {
  dataset_name: string;
  dataset_key: string;
  equipment_id?: string;
  sensor_id?: string;
  selected_columns: string[];
  window_size: number;
  algo_code: string;
  algo_name: string;
  params: Record<string, unknown>;
  auto_train_enabled: boolean;
  scheduler_interval_sec: number;
  min_new_feature_count: number;
  min_total_feature_count: number;
  recent_window_limit?: number | null;
};

export type ModelTrainAutoPolicyStatus = {
  policy_id: string;
  dataset_key: string;
  dataset_label: string;
  dataset_name: string | null;
  equipment_id: string;
  source_collection: string | null;
  target_collection: string | null;
  selected_columns: string[];
  window_size: number;
  window_mode: string | null;
  feature_stats: string[];
  scheduler_enabled: boolean | null;
  algo_code: string;
  algo_name: string;
  params: Record<string, unknown>;
  auto_train_enabled: boolean;
  scheduler_interval_sec: number;
  last_status: string | null;
  last_window_end: string | null;
  last_checkpoint_value: string | null;
  feature_config_source: string | null;
  feature_config_message: string | null;
  model_scheduler_interval_sec: number;
  model_scheduler_interval_minutes: number | null;
  min_new_feature_count: number;
  min_total_feature_count: number;
  recent_window_limit: number | null;
  pending_new_feature_count: number;
  total_feature_count: number;
  last_train_at: string | null;
  last_train_status: string | null;
  last_run_id: string | null;
  last_train_window_end: string | null;
  last_skip_reason: string | null;
  last_error_message: string | null;
  last_checked_at: string | null;
  last_run_started_at: string | null;
  last_run_finished_at: string | null;
  run_history_count: number;
  updated_at: string | null;
  created_at: string | null;
};

export type ModelTrainAutoPolicyListData = {
  scheduler_enabled: boolean;
  scheduler_fixed_delay_ms: number;
  policies: ModelTrainAutoPolicyStatus[];
};

export type ModelTrainAutoTriggerRequest = {
  policy_id?: string;
  dataset_key?: string;
  algo_code?: string;
  requested_by_role: string;
  force_run?: boolean;
};

export type ModelTrainAutoTriggerResult = {
  policy_id: string;
  dataset_label: string;
  status: string;
  message: string;
  run_id: string | null;
  processed_window_count: number;
  saved_result_count: number;
  new_feature_count: number;
  total_feature_count: number;
};

export type ModelTrainAutoTriggerData = {
  requested_policy_count: number;
  executed_policy_count: number;
  success_policy_count: number;
  results: ModelTrainAutoTriggerResult[];
};

export type AnomalyResultRow = {
  run_id: string;
  dataset_key?: string | null;
  equipment_id: string;
  lot_no?: string | null;
  window_start: number | string | null;
  window_end: number | string | null;
  input_features: Record<string, unknown> | null;
  anomaly_score: number | null;
  is_anomaly: boolean | null;
  health_index?: number | null;
  status?: string | null;
  reg_date: string | null;
};

export type AnomalyResultData = {
  anomalyResults: AnomalyResultRow[];
};

export type AnomalyAlgorithmOption = {
  algo_code: string;
  algo_name: string;
};

export type AnomalyRunOption = {
  run_id: string;
  policy_id: string | null;
  algo_code: string;
  algo_name: string;
  dataset_key: string | null;
  dataset_name: string | null;
  equipment_id: string | null;
  trigger_type: string | null;
  window_size: number | null;
  status: string | null;
  reg_date: string | null;
  updated_at: string | null;
};

export type AnomalyRunOptionsData = {
  algorithms: AnomalyAlgorithmOption[];
  runs: AnomalyRunOption[];
  latest_run_id: string | null;
  latest_success_run_id: string | null;
};

export type AnomalyRunDetail = {
  run_id: string;
  policy_id: string | null;
  algo_code: string;
  algo_name: string;
  dataset_key: string | null;
  dataset_name: string | null;
  equipment_id: string | null;
  sensor_id: string | null;
  trigger_type: string | null;
  status: string | null;
  selected_columns: string[];
  window_size: number | null;
  params: Record<string, unknown>;
  reg_date: string | null;
  updated_at: string | null;
};

export type AnomalyResultPoint = {
  run_id: string;
  algo_code: string;
  algo_name: string;
  dataset_key: string | null;
  equipment_id: string | null;
  status: string | null;
  anomaly_score: number | null;
  health_index: number | null;
  is_anomaly: boolean | null;
  window_start: string | null;
  window_end: string | null;
  reg_date: string | null;
  input_features: Record<string, unknown>;
};

export type AnomalyResultSummary = {
  status: string;
  latest_anomaly_score: number | null;
  avg_anomaly_score: number | null;
  latest_health_index: number | null;
  avg_health_index: number | null;
  integrated_health: number | null;
  integrated_status: string | null;
  if_normalized_health: number | null;
  ae_normalized_health: number | null;
  if_score_raw: number | null;
  ae_score_raw: number | null;
  anomaly_count: number;
  total_count: number;
};

export type AnomalyResultViewData = {
  run: AnomalyRunDetail | null;
  summary: AnomalyResultSummary;
  anomaly_results: AnomalyResultPoint[];
  selected_dataset_key?: string | null;
  selected_equipment_id?: string | null;
  selected_run_id?: string | null;
};

export type HomeInsightTrendPoint = {
  date: string;
  count: number;
};

export type HomeInsightTopSensor = {
  sensor: string;
  count: number;
};

export type HomeInsightRecentWindow = {
  id: string;
  windowStart: string | null;
  windowEnd: string | null;
  status: string;
  anomalyScore: number | null;
  causes: string[];
};

export type HomeInsightData = {
  runId: string | null;
  trend: HomeInsightTrendPoint[];
  topSensors: HomeInsightTopSensor[];
  recentWindows: HomeInsightRecentWindow[];
};

export type ParamFormValueMap = Record<string, string>;

export type ParamValidationErrorMap = Record<string, string | null>;

export type ModelTrainParamItem = AlgorithmParamItem;

export type AiOverviewActiveModel = {
  algo_code: string | null;
  algo_name: string | null;
  active_policy_id: string | null;
  dataset_key: string | null;
  dataset_label: string | null;
  window_size: number | null;
  selected_column_count: number;
  updated_at: string | null;
};

export type AiOverviewLatestRun = {
  run_id: string | null;
  status: string | null;
  trigger_type: string | null;
  started_at?: string | null;
  ended_at?: string | null;
  executed_at: string | null;
  algo_code: string | null;
  algo_name: string | null;
  dataset_key: string | null;
  message: string | null;
};

export type AiOverviewAnomalySummary = {
  avg_anomaly_score: number | null;
  avg_health_index: number | null;
  anomaly_count: number;
  total_count: number;
  status_counts: Record<string, number>;
};

export type AiOverviewFeatureImportance = {
  rank: number;
  feature: string;
  importance: number | null;
};

export type AiOverviewSupervisedSummary = {
  accuracy: number | null;
  precision: number | null;
  recall: number | null;
  f1_score: number | null;
  test_count: number;
  train_count: number;
  total_count: number;
  excluded_unknown_count: number;
  normal_count: number;
  anomaly_count: number;
  tp: number;
  tn: number;
  fp: number;
  fn: number;
  correct_count: number;
  misclassified_count: number;
  classification_result_count: number;
  latest_eval_executed_at: string | null;
  feature_importance_top: AiOverviewFeatureImportance[];
};

export type AiOverviewFeatureSummary = {
  dataset_key: string | null;
  dataset_label: string | null;
  source_dataset_name: string | null;
  total_feature_count: number;
  latest_feature_created_at: string | null;
  window_size: number | null;
  selected_column_count: number;
};

export type AiOverviewLabeledDataSummary = {
  source_collection: string | null;
  dataset_key: string | null;
  label_version: string | null;
  total_count: number;
  train_count: number;
  test_count: number;
  excluded_unknown_count: number;
  normal_count: number;
  anomaly_count: number;
};

export type AiOverviewDatasetModel = {
  dataset_key: string | null;
  dataset_label: string | null;
  source_collection: string | null;
  active_policy_id: string | null;
  active_algo_code: string | null;
  active_algo_name: string | null;
  summary_type: string | null;
  window_size: number | null;
  selected_column_count: number;
  reg_date: string | null;
  updated_at: string | null;
  latest_run: AiOverviewLatestRun;
  summary: AiOverviewAnomalySummary;
  supervised_summary: AiOverviewSupervisedSummary;
  feature_summary: AiOverviewFeatureSummary;
  labeled_data_summary: AiOverviewLabeledDataSummary;
};

export type AiOverviewData = {
  active_models: AiOverviewDatasetModel[];
  total_active_model_count: number;
  active_model: AiOverviewActiveModel;
  latest_run: AiOverviewLatestRun;
  anomaly_summary: AiOverviewAnomalySummary;
  supervised_summary: AiOverviewSupervisedSummary;
  feature_summary: AiOverviewFeatureSummary;
  labeled_data_summary: AiOverviewLabeledDataSummary;
  refreshed_at: string | null;
};
