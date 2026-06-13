import { apiClient } from './apiClient';
import type {
  FetchSupervisedPredictionsParams,
  FetchSupervisedRunsParams,
  FetchSupervisedTrendParams,
  SupervisedDistribution,
  SupervisedErrors,
  SupervisedPredictionPage,
  SupervisedRun,
  SupervisedSummary,
  SupervisedTrendPoint,
} from '../types/supervisedResult';

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

export const supervisedResultService = {
  fetchRuns(params?: FetchSupervisedRunsParams) {
    const queryParams = new URLSearchParams();
    appendOptionalQueryParam(queryParams, 'triggerType', params?.triggerType);
    appendOptionalQueryParam(queryParams, 'limit', params?.limit);
    const query = queryParams.toString();
    const path = query ? `/api/supervised/result/runs?${query}` : '/api/supervised/result/runs';
    return apiClient.request<SupervisedRun[]>(path);
  },

  fetchSummary(runId: string) {
    const queryParams = new URLSearchParams();
    queryParams.set('runId', runId);
    return apiClient.request<SupervisedSummary>(`/api/supervised/result/summary?${queryParams.toString()}`);
  },

  fetchTrend(params?: FetchSupervisedTrendParams) {
    const queryParams = new URLSearchParams();
    appendOptionalQueryParam(queryParams, 'triggerType', params?.triggerType);
    appendOptionalQueryParam(queryParams, 'limit', params?.limit);
    const query = queryParams.toString();
    const path = query ? `/api/supervised/result/trend?${query}` : '/api/supervised/result/trend';
    return apiClient.request<SupervisedTrendPoint[]>(path);
  },

  fetchDistribution(runId: string) {
    const queryParams = new URLSearchParams();
    queryParams.set('runId', runId);
    return apiClient.request<SupervisedDistribution>(
      `/api/supervised/result/distribution?${queryParams.toString()}`,
    );
  },

  fetchErrors(runId: string, limit = 5) {
    const queryParams = new URLSearchParams();
    queryParams.set('runId', runId);
    appendOptionalQueryParam(queryParams, 'limit', limit);
    return apiClient.request<SupervisedErrors>(`/api/supervised/result/errors?${queryParams.toString()}`);
  },

  fetchPredictions(params: FetchSupervisedPredictionsParams) {
    const queryParams = new URLSearchParams();
    queryParams.set('runId', params.runId);
    appendOptionalQueryParam(queryParams, 'filter', params.filter);
    appendOptionalQueryParam(queryParams, 'from', params.from);
    appendOptionalQueryParam(queryParams, 'to', params.to);
    appendOptionalQueryParam(queryParams, 'page', params.page);
    appendOptionalQueryParam(queryParams, 'size', params.size);
    return apiClient.request<SupervisedPredictionPage>(
      `/api/supervised/result/predictions?${queryParams.toString()}`,
    );
  },
};
