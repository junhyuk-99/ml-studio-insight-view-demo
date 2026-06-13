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

export const modelTrainService = {
  getFeatureDatasets() {
    return apiClient.request<FeatureDatasetListData>('/api/modeltrain/feature-datasets');
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
    return apiClient.request<AnomalyRunOptionsData>(`/api/modeltrain/anomaly/runs?${queryParams.toString()}`);
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

    return apiClient.request<AnomalyResultViewData>(`/api/modeltrain/anomaly/results?${queryParams.toString()}`);
  },

  getAnomalyRunDetail(runId: string, limit = 1000) {
    const encodedRunId = encodeURIComponent(runId.trim());
    const safeLimit = Number.isFinite(limit) ? Math.min(Math.max(1, Math.floor(limit)), 2000) : 1000;
    return apiClient.request<AnomalyResultViewData>(
      `/api/modeltrain/anomaly/runs/${encodedRunId}?limit=${safeLimit}`,
    );
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
