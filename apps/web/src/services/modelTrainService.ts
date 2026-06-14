import { apiClient } from './apiClient';
import type {
  AiOverviewData,
  AnomalyResultViewData,
  AnomalyRunOptionsData,
  AnomalyResultData,
  ExecuteModelRunData,
  ExecuteModelRunRequest,
  FeatureDatasetListData,
  HomeInsightData,
  ModelTrainAutoPolicyListData,
  ModelTrainAutoPolicyStatus,
  ModelTrainAutoPolicyUpsertRequest,
  ModelTrainAutoTriggerData,
  ModelTrainAutoTriggerRequest,
  ModelRunData,
  ModelRunSaveRequest,
} from '../types/modelTrain';

const DEMO_DATASET_KEY = 'DEMO_DATASET_MANUFACTURING_AI';
const FALLBACK_FEATURE_DATASETS: FeatureDatasetListData = {
  feature_datasets: [
    {
      dataset_key: DEMO_DATASET_KEY,
      dataset_label: 'Synthetic demo fallback',
      dataset_name: 'Demo Manufacturing AI Dataset',
      equipment_id: null,
      source_collection: 'THISHMIDATA',
      target_collection: 'thisfeature',
      selected_columns: ['TEMP_PV', 'PRESSURE_PV', 'MOTOR_CURRENT_PV', 'VIBRATION_RMS_PV'],
      window_size: 30,
      window_mode: 'fixed_count_only',
      feature_stats: ['MEAN', 'STD', 'MIN', 'MAX'],
      scheduler_enabled: false,
      scheduler_interval_sec: 600,
      last_status: 'READY',
      last_window_end: '2026-06-10T10:59:00Z',
      last_checkpoint_value: '2026-06-10T10:59:00Z',
      config_source: 'Synthetic demo fallback',
      config_message: 'Synthetic demo fallback',
      SOURCE_TYPE_CODE: 'DATABASE',
      SOURCE_DTL_CODE: 'MONGODB',
      SOURCE_FILE: null,
    },
  ],
};

const FALLBACK_RUNS: AnomalyRunOptionsData = {
  algorithms: [
    { algo_code: 'ISOLATION_FOREST', algo_name: 'Isolation Forest' },
    { algo_code: 'AUTOENCODER', algo_name: 'AutoEncoder' },
    { algo_code: 'RANDOM_FOREST', algo_name: 'Random Forest' },
  ],
  runs: [
    {
      run_id: 'DEMO-RUN-IF-001',
      policy_id: 'DEMO-POLICY-IF-DEFAULT',
      algo_code: 'ISOLATION_FOREST',
      algo_name: 'Isolation Forest',
      dataset_key: DEMO_DATASET_KEY,
      dataset_name: 'Synthetic demo fallback',
      equipment_id: 'DEMO-MC-001',
      trigger_type: 'MANUAL',
      window_size: 30,
      status: 'SUCCESS',
      reg_date: '2026-06-10T10:35:00Z',
      updated_at: '2026-06-10T10:36:00Z',
    },
  ],
  latest_run_id: 'DEMO-RUN-IF-001',
  latest_success_run_id: 'DEMO-RUN-IF-001',
};

const FALLBACK_ANOMALY_RESULTS = [
  {
    run_id: 'DEMO-RUN-IF-001',
    algo_code: 'ISOLATION_FOREST',
    algo_name: 'Isolation Forest',
    dataset_key: DEMO_DATASET_KEY,
    equipment_id: 'DEMO-MC-001',
    status: 'NORMAL',
    anomaly_score: 0.32,
    health_index: 91.5,
    is_anomaly: false,
    window_start: '2026-06-10T09:00:00Z',
    window_end: '2026-06-10T09:29:00Z',
    reg_date: '2026-06-10T10:36:00Z',
    input_features: { source: 'Synthetic demo fallback', TEMP_PV_MEAN: 741.2 },
  },
  {
    run_id: 'DEMO-RUN-IF-001',
    algo_code: 'ISOLATION_FOREST',
    algo_name: 'Isolation Forest',
    dataset_key: DEMO_DATASET_KEY,
    equipment_id: 'DEMO-MC-002',
    status: 'NORMAL',
    anomaly_score: 0.47,
    health_index: 84.2,
    is_anomaly: false,
    window_start: '2026-06-10T09:30:00Z',
    window_end: '2026-06-10T09:59:00Z',
    reg_date: '2026-06-10T10:37:00Z',
    input_features: { source: 'Synthetic demo fallback', TEMP_PV_MEAN: 738.7 },
  },
  {
    run_id: 'DEMO-RUN-IF-001',
    algo_code: 'ISOLATION_FOREST',
    algo_name: 'Isolation Forest',
    dataset_key: DEMO_DATASET_KEY,
    equipment_id: 'DEMO-MC-003',
    status: 'WARNING',
    anomaly_score: 0.62,
    health_index: 72.4,
    is_anomaly: true,
    window_start: '2026-06-10T10:00:00Z',
    window_end: '2026-06-10T10:29:00Z',
    reg_date: '2026-06-10T10:38:00Z',
    input_features: { source: 'Synthetic demo fallback', TEMP_PV_MEAN: 743.1 },
  },
  {
    run_id: 'DEMO-RUN-IF-001',
    algo_code: 'ISOLATION_FOREST',
    algo_name: 'Isolation Forest',
    dataset_key: DEMO_DATASET_KEY,
    equipment_id: 'DEMO-MC-001',
    status: 'CRITICAL',
    anomaly_score: 0.81,
    health_index: 58.7,
    is_anomaly: true,
    window_start: '2026-06-10T10:30:00Z',
    window_end: '2026-06-10T10:59:00Z',
    reg_date: '2026-06-10T10:39:00Z',
    input_features: { source: 'Synthetic demo fallback', TEMP_PV_MEAN: 746.8 },
  },
  {
    run_id: 'DEMO-RUN-IF-001',
    algo_code: 'ISOLATION_FOREST',
    algo_name: 'Isolation Forest',
    dataset_key: DEMO_DATASET_KEY,
    equipment_id: 'DEMO-MC-002',
    status: 'NORMAL',
    anomaly_score: 0.38,
    health_index: 88.1,
    is_anomaly: false,
    window_start: '2026-06-10T11:00:00Z',
    window_end: '2026-06-10T11:29:00Z',
    reg_date: '2026-06-10T10:40:00Z',
    input_features: { source: 'Synthetic demo fallback', TEMP_PV_MEAN: 739.4 },
  },
];

const FALLBACK_ANOMALY_VIEW: AnomalyResultViewData = {
  run: {
    run_id: 'DEMO-RUN-IF-001',
    policy_id: 'DEMO-POLICY-IF-DEFAULT',
    algo_code: 'ISOLATION_FOREST',
    algo_name: 'Isolation Forest',
    dataset_key: DEMO_DATASET_KEY,
    dataset_name: 'Synthetic demo fallback',
    equipment_id: 'DEMO-MC-001',
    sensor_id: null,
    trigger_type: 'MANUAL',
    status: 'SUCCESS',
    selected_columns: ['TEMP_PV_MEAN', 'PRESSURE_PV_MEAN', 'MOTOR_CURRENT_PV_MEAN', 'VIBRATION_RMS_PV_MEAN'],
    window_size: 30,
    params: { N_ESTIMATORS: 100, CONTAMINATION: 0.05 },
    reg_date: '2026-06-10T10:35:00Z',
    updated_at: '2026-06-10T10:36:00Z',
  },
  summary: {
    status: 'Synthetic demo fallback',
    latest_anomaly_score: 0.38,
    avg_anomaly_score: 0.52,
    latest_health_index: 88.1,
    avg_health_index: 78.98,
    integrated_health: 78.98,
    integrated_status: 'WARNING',
    if_normalized_health: 78.98,
    ae_normalized_health: null,
    if_score_raw: 0.52,
    ae_score_raw: null,
    anomaly_count: 2,
    total_count: FALLBACK_ANOMALY_RESULTS.length,
  },
  anomaly_results: FALLBACK_ANOMALY_RESULTS,
  selected_dataset_key: DEMO_DATASET_KEY,
  selected_equipment_id: null,
  selected_run_id: 'DEMO-RUN-IF-001',
};

export const modelTrainService = {
  getFeatureDatasets() {
    return apiClient
      .request<FeatureDatasetListData>('/api/modeltrain/feature-datasets')
      .then((data) => (data.feature_datasets?.length ? data : FALLBACK_FEATURE_DATASETS))
      .catch(() => FALLBACK_FEATURE_DATASETS);
  },

  createModelRun(request: ModelRunSaveRequest) {
    return apiClient.request<ModelRunData>('/api/modeltrain/model-runs', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  executeModelRun(request: ExecuteModelRunRequest) {
    return apiClient.request<ExecuteModelRunData>('/api/modeltrain/execute', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  getAutoPolicies(datasetKey?: string) {
    const normalizedDatasetKey = datasetKey?.trim();
    if (normalizedDatasetKey) {
      const encodedDatasetKey = encodeURIComponent(normalizedDatasetKey);
      return apiClient.request<ModelTrainAutoPolicyListData>(`/api/modeltrain/auto-policies?dataset_key=${encodedDatasetKey}`);
    }
    return apiClient.request<ModelTrainAutoPolicyListData>('/api/modeltrain/auto-policies');
  },

  upsertAutoPolicy(request: ModelTrainAutoPolicyUpsertRequest) {
    return apiClient.request<ModelTrainAutoPolicyStatus>('/api/modeltrain/auto-policies', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  triggerAutoPolicies(request: ModelTrainAutoTriggerRequest) {
    return apiClient.request<ModelTrainAutoTriggerData>('/api/modeltrain/auto-policies/trigger', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  getAnomalyResults(
    runId: string,
    limit = 500,
    options?: {
      datasetKey?: string;
      equipmentId?: string;
    },
  ) {
    const normalizedRunId = runId.trim();
    const safeLimit = Number.isFinite(limit) ? Math.min(Math.max(1, Math.floor(limit)), 2000) : 500;
    const queryParams = new URLSearchParams();
    queryParams.set('run_id', normalizedRunId);
    queryParams.set('limit', String(safeLimit));
    if (options?.datasetKey && options.datasetKey.trim().length > 0) {
      queryParams.set('dataset_key', options.datasetKey.trim());
    }
    if (options?.equipmentId && options.equipmentId.trim().length > 0) {
      queryParams.set('equipment_id', options.equipmentId.trim());
    }
    return apiClient.request<AnomalyResultData>(
      `/api/modeltrain/anomaly-results?${queryParams.toString()}`,
    );
  },

  getAnomalyRunOptions(options?: {
    algoCode?: string;
    datasetKey?: string;
    equipmentId?: string;
    includeNonSuccess?: boolean;
    limit?: number;
  }) {
    const safeLimit = Number.isFinite(options?.limit)
      ? Math.min(Math.max(1, Math.floor(options?.limit as number)), 500)
      : 50;
    const queryParams = new URLSearchParams();
    queryParams.set('limit', String(safeLimit));
    if (options?.algoCode && options.algoCode.trim().length > 0) {
      queryParams.set('algo_code', options.algoCode.trim());
    }
    if (options?.datasetKey && options.datasetKey.trim().length > 0) {
      queryParams.set('dataset_key', options.datasetKey.trim());
    }
    if (options?.equipmentId && options.equipmentId.trim().length > 0) {
      queryParams.set('equipment_id', options.equipmentId.trim());
    }
    if (typeof options?.includeNonSuccess === 'boolean') {
      queryParams.set('include_non_success', String(options.includeNonSuccess));
    }
    return apiClient
      .request<AnomalyRunOptionsData>(`/api/modeltrain/anomaly/runs?${queryParams.toString()}`)
      .then((data) => (data.runs?.length ? data : FALLBACK_RUNS))
      .catch(() => FALLBACK_RUNS);
  },

  getAnomalyResultView(options: {
    algoCode?: string;
    runId?: string;
    datasetKey?: string;
    equipmentId?: string;
    limit?: number;
  }) {
    const queryParams = new URLSearchParams();
    const safeLimit = Number.isFinite(options.limit)
      ? Math.min(Math.max(1, Math.floor(options.limit as number)), 2000)
      : 1000;
    queryParams.set('limit', String(safeLimit));

    if (options.algoCode && options.algoCode.trim().length > 0) {
      queryParams.set('algo_code', options.algoCode.trim());
    }
    if (options.runId && options.runId.trim().length > 0) {
      queryParams.set('run_id', options.runId.trim());
    }
    if (options.datasetKey && options.datasetKey.trim().length > 0) {
      queryParams.set('dataset_key', options.datasetKey.trim());
    }
    if (options.equipmentId && options.equipmentId.trim().length > 0) {
      queryParams.set('equipment_id', options.equipmentId.trim());
    }

    return apiClient
      .request<AnomalyResultViewData>(`/api/modeltrain/anomaly/results?${queryParams.toString()}`)
      .then((data) => (data.anomaly_results?.length ? data : FALLBACK_ANOMALY_VIEW))
      .catch(() => FALLBACK_ANOMALY_VIEW);
  },

  getAnomalyRunDetail(runId: string, limit = 1000) {
    const encodedRunId = encodeURIComponent(runId.trim());
    const safeLimit = Number.isFinite(limit) ? Math.min(Math.max(1, Math.floor(limit)), 2000) : 1000;
    return apiClient.request<AnomalyResultViewData>(
      `/api/modeltrain/anomaly/runs/${encodedRunId}?limit=${safeLimit}`,
    ).then((data) => (data.anomaly_results?.length ? data : FALLBACK_ANOMALY_VIEW))
      .catch(() => FALLBACK_ANOMALY_VIEW);
  },

  getOverview() {
    return apiClient.request<AiOverviewData>('/api/modeltrain/overview');
  },

  getHomeInsight(runId?: string | null) {
    const queryParams = new URLSearchParams();
    if (runId && runId.trim().length > 0) {
      queryParams.set('run_id', runId.trim());
    }
    const query = queryParams.toString();
    return apiClient.request<HomeInsightData>(
      query.length > 0 ? `/api/modeltrain/overview/home-insight?${query}` : '/api/modeltrain/overview/home-insight',
    );
  },
};
