import { apiClient } from './apiClient';
import type {
  AnomalyCauseDetail,
  AnomalyCauseListResponse,
  AnomalyCauseRun,
  FetchAnomalyCauseDetailParams,
  FetchAnomalyCauseListParams,
  RecalculateAnomalyCauseRequest,
  RecalculateAnomalyCauseRunResult,
  RecalculateAnomalyCauseRunRequest,
} from '../types/anomalyCause';

function appendOptionalQueryParam(
  queryParams: URLSearchParams,
  key: string,
  value: string | number | null | undefined,
) {
  if (value == null) {
    return;
  }
  const normalized = String(value).trim();
  if (normalized.length === 0) {
    return;
  }
  queryParams.set(key, normalized);
}

export const anomalyCauseService = {
  fetchAnomalyCauseRuns() {
    return apiClient.request<AnomalyCauseRun[]>('/api/anomaly-cause/runs');
  },

  fetchAnomalyCauseList(params: FetchAnomalyCauseListParams) {
    const queryParams = new URLSearchParams();
    queryParams.set('runId', params.runId);
    queryParams.set('datasetKey', params.datasetKey);
    queryParams.set('equipmentId', params.equipmentId);
    appendOptionalQueryParam(queryParams, 'status', params.status);
    appendOptionalQueryParam(queryParams, 'from', params.from);
    appendOptionalQueryParam(queryParams, 'to', params.to);
    appendOptionalQueryParam(queryParams, 'page', params.page);
    appendOptionalQueryParam(queryParams, 'size', params.size);

    return apiClient.request<AnomalyCauseListResponse>(
      `/api/anomaly-cause/list?${queryParams.toString()}`,
    );
  },

  fetchAnomalyCauseDetail(params: FetchAnomalyCauseDetailParams) {
    const queryParams = new URLSearchParams();
    queryParams.set('runId', params.runId);
    queryParams.set('datasetKey', params.datasetKey);
    queryParams.set('equipmentId', params.equipmentId);
    queryParams.set('windowStart', params.windowStart);
    queryParams.set('windowEnd', params.windowEnd);

    return apiClient.request<AnomalyCauseDetail>(
      `/api/anomaly-cause/detail?${queryParams.toString()}`,
    );
  },

  recalculateAnomalyCause(params: RecalculateAnomalyCauseRequest) {
    return apiClient.request<AnomalyCauseDetail>('/api/anomaly-cause/recalculate', {
      method: 'POST',
      body: JSON.stringify(params),
    });
  },

  recalculateAnomalyCauseRun(params: RecalculateAnomalyCauseRunRequest) {
    return apiClient.request<RecalculateAnomalyCauseRunResult>(
      '/api/anomaly-cause/recalculate/run',
      {
        method: 'POST',
        body: JSON.stringify(params),
      },
    );
  },
};
