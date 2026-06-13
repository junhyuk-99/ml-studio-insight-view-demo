export type DataExplorationDatasetOption = {
  datasetKey: string;
  datasetName: string | null;
  displayName: string | null;
  typeCode: string | null;
  dtlCode: string | null;
  sourceCollection: string | null;
  datasetPurpose: string | null;
  featureEnabled: boolean | null;
  sortNo: number | null;
};

export type DataExplorationCurrentRange = {
  from: string | null;
  to: string | null;
};

export type DataExplorationAppliedMatchFilter = Record<string, unknown>;

export type HistogramFieldOption = {
  field: string;
  field_name?: string;
  numeric?: boolean;
  sample_count?: number;
  non_null_count?: number;
  null_count?: number;
  has_value?: boolean;
  default_selected: boolean;
  sequence_field: boolean;
};

export type HistogramFieldListData = {
  dataset_key: string | null;
  dataset_name?: string | null;
  display_name?: string | null;
  source_collection?: string | null;
  applied_match_filter?: DataExplorationAppliedMatchFilter;
  current_range?: DataExplorationCurrentRange | null;
  min_timestamp: string | null;
  max_timestamp: string | null;
  default_from: string | null;
  default_to: string | null;
  row_count: number;
  fields: HistogramFieldOption[];
  default_selected_fields: string[];
  group_by_fields: string[];
  default_group_by: string | null;
};

export type HistogramBin = {
  index: number;
  start: number;
  end: number;
  count: number;
};

export type HistogramFieldData = {
  field: string;
  sample_count: number;
  min: number | null;
  max: number | null;
  avg: number | null;
  bins: HistogramBin[];
};

export type HistogramDataResponse = {
  dataset_key?: string | null;
  dataset_name?: string | null;
  display_name?: string | null;
  source_collection?: string | null;
  applied_match_filter?: DataExplorationAppliedMatchFilter;
  current_range?: DataExplorationCurrentRange | null;
  from: string;
  to: string;
  bins: number;
  total_row_count: number;
  field_histograms: HistogramFieldData[];
};

export type HistogramDataRequest = {
  dataset_key?: string;
  datasetKey?: string;
  equipment_id?: string;
  equipmentId?: string;
  from: string;
  to: string;
  selected_fields: string[];
  bins?: number;
};

export type TimeseriesPoint = {
  timestamp: string;
  value: number;
};

export type TimeseriesFieldData = {
  field: string;
  sample_count: number;
  min: number | null;
  max: number | null;
  avg: number | null;
  points: TimeseriesPoint[];
};

export type TimeseriesDataResponse = {
  dataset_key?: string | null;
  dataset_name?: string | null;
  display_name?: string | null;
  source_collection?: string | null;
  applied_match_filter?: DataExplorationAppliedMatchFilter;
  current_range?: DataExplorationCurrentRange | null;
  from: string;
  to: string;
  max_points: number;
  total_row_count: number;
  sampled_row_count: number;
  sampling_applied: boolean;
  sampling_step: number;
  field_timeseries: TimeseriesFieldData[];
};

export type TimeseriesDataRequest = {
  dataset_key?: string;
  datasetKey?: string;
  equipment_id?: string;
  equipmentId?: string;
  from: string;
  to: string;
  selected_fields: string[];
  max_points?: number;
};

export type CorrelationHeatmapDataResponse = {
  dataset_key?: string | null;
  dataset_name?: string | null;
  display_name?: string | null;
  source_collection?: string | null;
  applied_match_filter?: DataExplorationAppliedMatchFilter;
  current_range?: DataExplorationCurrentRange | null;
  from: string;
  to: string;
  method: string;
  max_rows: number;
  total_row_count: number;
  effective_row_count: number;
  sampled_row_count: number;
  sampling_applied: boolean;
  sampling_step: number;
  fields: string[];
  matrix: (number | null)[][];
  pair_sample_counts: number[][];
};

export type CorrelationHeatmapDataRequest = {
  dataset_key?: string;
  datasetKey?: string;
  equipment_id?: string;
  equipmentId?: string;
  from: string;
  to: string;
  selected_fields: string[];
  max_rows?: number;
};

export type BoxplotFieldData = {
  field: string;
  sample_count: number;
  min: number | null;
  q1: number | null;
  median: number | null;
  q3: number | null;
  max: number | null;
  whisker_low: number | null;
  whisker_high: number | null;
  outlier_count: number;
  outliers: number[];
};

export type BoxplotDataResponse = {
  dataset_key?: string | null;
  dataset_name?: string | null;
  display_name?: string | null;
  source_collection?: string | null;
  applied_match_filter?: DataExplorationAppliedMatchFilter;
  current_range?: DataExplorationCurrentRange | null;
  from: string;
  to: string;
  max_rows: number;
  total_row_count: number;
  effective_row_count: number;
  sampled_row_count: number;
  sampling_applied: boolean;
  sampling_step: number;
  field_boxplots: BoxplotFieldData[];
};

export type BoxplotDataRequest = {
  dataset_key?: string;
  datasetKey?: string;
  equipment_id?: string;
  equipmentId?: string;
  from: string;
  to: string;
  selected_fields: string[];
  max_rows?: number;
  group_by?: string;
  max_outliers_per_field?: number;
};

export type ProcessFlowEquipmentOption = {
  MCCODE: string | null;
  MCNAME: string | null;
  process_type?: string | null;
  opstat_code_group?: string | null;
  ai_use_flag?: string | null;
  dataset_key?: string | null;
};

export type ProcessFlowOpstatMapping = {
  code: number;
  label: string;
  sortno: number;
  colorKey: string;
};

export type ProcessFlowRange = {
  start: string;
  end: string;
  timezone: string;
};

export type ProcessFlowLatest = {
  prdtime: string;
  opstat: number | null;
  opstatLabel: string | null;
  opalarm: string | null;
  workorder: string | null;
  patPgm: string | null;
  segmentNo: number | null;
  segmentTime: number | null;
  segmentTotal: number | null;
  t1Pv: number | null;
  t2Pv: number | null;
  t1Sv: number | null;
  t2Sv: number | null;
  co2Pv: number | null;
  coPv: number | null;
  cpPv: number | null;
};

export type ProcessFlowCurrentState = {
  code: number | null;
  label: string | null;
  startedAt: string;
  elapsedSeconds: number;
  rowCount: number;
};

export type ProcessFlowTemperaturePoint = {
  prdtime: string;
  opstat: number | null;
  opstatLabel: string | null;
  t1Pv: number | null;
  t2Pv: number | null;
  t1Sv: number | null;
  t2Sv: number | null;
  co2Pv: number | null;
  coPv: number | null;
  cpPv: number | null;
  opalarm: string | null;
  workorder: string | null;
  patPgm: string | null;
  segmentNo: number | null;
  segmentTime: number | null;
  segmentTotal: number | null;
};

export type ProcessFlowSegment = {
  opstat: number | null;
  label: string | null;
  start: string;
  end: string;
  durationSeconds: number;
  rowCount: number;
};

export type ProcessFlowStageSummary = {
  opstat: number | null;
  label: string | null;
  start: string;
  end: string;
  durationSeconds: number;
  rowCount: number;
  avgT1Pv: number | null;
  avgT2Pv: number | null;
  avgT1Sv: number | null;
  avgT2Sv: number | null;
  minT1Pv: number | null;
  maxT1Pv: number | null;
  minT2Pv: number | null;
  maxT2Pv: number | null;
};

export type ProcessFlowEvent = {
  time: string;
  type: 'STATE_CHANGE' | 'ALARM_CHANGE' | 'WORKORDER_CHANGE' | string;
  fromCode: number | null;
  fromLabel: string | null;
  toCode: number | null;
  toLabel: string | null;
  message: string | null;
};

export type ProcessFlowResponse = {
  datasetKey: string | null;
  sourceCollection: string;
  mccode: string;
  equipmentName: string | null;
  opstatGroup: string | null;
  opstatMapping: ProcessFlowOpstatMapping[];
  range: ProcessFlowRange;
  latest: ProcessFlowLatest | null;
  currentState: ProcessFlowCurrentState | null;
  temperatureSeries: ProcessFlowTemperaturePoint[];
  processSegments: ProcessFlowSegment[];
  stageSummary: ProcessFlowStageSummary[];
  eventTimeline: ProcessFlowEvent[];
  warnings: string[];
};

export type ProcessFlowRequest = {
  datasetKey?: string;
  mccode?: string;
  start: string;
  end: string;
  opstats?: number[];
  fields?: string[];
  limit?: number;
  autoRefresh?: boolean;
};
