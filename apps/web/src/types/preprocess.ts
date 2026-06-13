export type DataSourceDataset = {
  datasetKey: string;
  datasetName: string | null;
  displayName: string | null;
  sourceCollection: string | null;
  targetFeatureCollection: string | null;
  datasetPurpose: string | null;
  featureEnabled: boolean | null;
  labelField: string | null;
  sortNo: number | null;
  equipmentGroup: string | null;
  equipmentGroupName: string | null;
};

export type DataSourceDetail = {
  dtlCode: string;
  dtlName: string;
  sortNo: number | null;
  datasets: DataSourceDataset[];
};

export type DataSourceType = {
  typeCode: string;
  typeName: string;
  sortNo: number | null;
  details: DataSourceDetail[];
};

export type DataSourceListData = {
  dataTypes: DataSourceType[];
};

export type RawPreviewRow = Record<string, unknown>;

export type DatasetKey = Record<string, string>;

export type RawDataPreviewData = {
  sourceCollection?: string | null;
  datasetKey?: string | null;
  datasetName?: string | null;
  datasetDisplayName?: string | null;
  availableColumns: string[];
  metadataColumns: string[];
  numericColumns: string[];
  columnLabels: Record<string, string>;
  datasetKeyColumns: string[];
  datasetKeys: DatasetKey[];
  rawRows: RawPreviewRow[];
};

export type PreprocessType = {
  prepTypeCd: string;
  prepTypeNm: string;
  desc: string | null;
  sortOrd: number | null;
};

export type PreprocessOption = {
  prepCd: string;
  prepNm: string;
  sortOrd: number | null;
};

export type PreprocessOptionData = {
  prepTypes: PreprocessType[];
  optionsByType: Record<string, PreprocessOption[]>;
};

export type FeatureGenerationMode = 'auto' | 'manual';

export type FeatureGenerationRequest = {
  equipment_id?: string;
  sensor_id?: string;
  dataset_key: string;
  selected_columns: string[];
  window_size: number;
  mode: FeatureGenerationMode;
};

export type FeatureGenerationData = {
  totalWindowCount: number;
  createdCount: number;
  skippedCount: number;
};

export type FeaturePreviewRow = Record<string, unknown>;

export type FeaturePreviewData = {
  datasetKey: string;
  availableColumns: string[];
  featureRows: FeaturePreviewRow[];
};

export type FeatureAutoJobStatus = {
  dataset_key: string;
  dataset_label: string;
  target_collection: string;
  window_size: number;
  selected_columns: string[];
  schedule_interval_seconds: number;
  use_yn: boolean;
  pending_raw_count: number;
  last_processed_timestamp: string | null;
  last_processed_row_id: string | null;
  last_window_end: string | null;
  last_run_status: string;
  last_run_started_at: string | null;
  last_run_finished_at: string | null;
  last_trigger_type: string | null;
  last_total_window_count: number;
  last_created_count: number;
  last_skipped_count: number;
  last_consumed_raw_count: number;
  last_error_message: string | null;
  updated_at: string | null;
};

export type FeatureAutoJobListData = {
  scheduler_enabled: boolean;
  scheduler_fixed_delay_ms: number;
  jobs: FeatureAutoJobStatus[];
};

export type FeatureAutoJobUpsertRequest = {
  dataset_name?: string;
  equipment_id?: string;
  sensor_id?: string;
  dataset_key: string;
  selected_columns: string[];
  window_size: number;
  target_collection?: string;
  schedule_interval_seconds?: number;
  use_yn?: boolean;
};

export type FeatureAutoTriggerRequest = {
  equipment_id?: string;
  sensor_id?: string;
  dataset_key?: string;
};

export type FeatureAutoTriggerResult = {
  dataset_label: string;
  status: string;
  message: string;
  total_window_count: number;
  created_count: number;
  skipped_count: number;
  consumed_raw_count: number;
};

export type FeatureAutoTriggerData = {
  requested_job_count: number;
  executed_job_count: number;
  success_job_count: number;
  results: FeatureAutoTriggerResult[];
};