import { apiClient } from './apiClient';
import type {
  FetchHealthIndexTrendParams,
  HealthIndexRun,
  HealthIndexTrendResponse,
} from '../types/healthIndex';

function appendOptionalQueryParam(
  queryParams: URLSearchParams,
  key: string,
  value: string | number | null | undefined,
) {
  if (value == null) {
    return;
  }
  const normalized = String(value).trim();
  if (!normalized) {
    return;
  }
  queryParams.set(key, normalized);
}

export const healthIndexService = {
  fetchHealthIndexRuns() {
    return apiClient.request<HealthIndexRun[]>('/api/health-index/runs');
  },

  fetchHealthIndexTrend(params: FetchHealthIndexTrendParams) {
    const queryParams = new URLSearchParams();
    queryParams.set('runId', params.runId);
    queryParams.set('datasetKey', params.datasetKey);
    appendOptionalQueryParam(queryParams, 'status', params.status);
    appendOptionalQueryParam(queryParams, 'from', params.from);
    appendOptionalQueryParam(queryParams, 'to', params.to);

    return apiClient.request<HealthIndexTrendResponse>(
      `/api/health-index/trend?${queryParams.toString()}`,
    );
  },
};
