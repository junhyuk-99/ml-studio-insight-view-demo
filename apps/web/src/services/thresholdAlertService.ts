import { apiClient } from './apiClient';
import type {
  FetchThresholdAlertTrendParams,
  FetchThresholdAlertListParams,
  FetchThresholdAlertSummaryParams,
  ThresholdAlertAckRequest,
  ThresholdAlertListItem,
  ThresholdAlertListResponse,
  ThresholdAlertRecalculateRunRequest,
  ThresholdAlertRecalculateRunResult,
  ThresholdAlertSummary,
  ThresholdAlertTrendResponse,
} from '../types/thresholdAlert';

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

export const thresholdAlertService = {
  fetchThresholdAlertSummary(params: FetchThresholdAlertSummaryParams) {
    const queryParams = new URLSearchParams();
    queryParams.set('datasetKey', params.datasetKey);
    appendOptionalQueryParam(queryParams, 'runId', params.runId);
    appendOptionalQueryParam(queryParams, 'from', params.from);
    appendOptionalQueryParam(queryParams, 'to', params.to);

    return apiClient.request<ThresholdAlertSummary>(
      `/api/threshold-alert/summary?${queryParams.toString()}`,
    );
  },

  fetchThresholdAlertList(params: FetchThresholdAlertListParams) {
    const queryParams = new URLSearchParams();
    queryParams.set('datasetKey', params.datasetKey);
    appendOptionalQueryParam(queryParams, 'runId', params.runId);
    appendOptionalQueryParam(queryParams, 'severity', params.severity);
    appendOptionalQueryParam(queryParams, 'status', params.status);
    appendOptionalQueryParam(queryParams, 'ackYn', params.ackYn);
    appendOptionalQueryParam(queryParams, 'from', params.from);
    appendOptionalQueryParam(queryParams, 'to', params.to);
    appendOptionalQueryParam(queryParams, 'page', params.page);
    appendOptionalQueryParam(queryParams, 'size', params.size);

    return apiClient.request<ThresholdAlertListResponse>(
      `/api/threshold-alert/list?${queryParams.toString()}`,
    );
  },

  fetchThresholdAlertTrend(params: FetchThresholdAlertTrendParams) {
    const queryParams = new URLSearchParams();
    queryParams.set('datasetKey', params.datasetKey);
    appendOptionalQueryParam(queryParams, 'runId', params.runId);
    appendOptionalQueryParam(queryParams, 'severity', params.severity);
    appendOptionalQueryParam(queryParams, 'status', params.status);
    appendOptionalQueryParam(queryParams, 'ackYn', params.ackYn);
    appendOptionalQueryParam(queryParams, 'from', params.from);
    appendOptionalQueryParam(queryParams, 'to', params.to);
    appendOptionalQueryParam(queryParams, 'limit', params.limit);

    return apiClient.request<ThresholdAlertTrendResponse>(
      `/api/threshold-alert/trend?${queryParams.toString()}`,
    );
  },

  recalculateThresholdAlertRun(body: ThresholdAlertRecalculateRunRequest) {
    return apiClient.request<ThresholdAlertRecalculateRunResult>(
      '/api/threshold-alert/recalculate/run',
      {
        method: 'POST',
        body: JSON.stringify(body),
      },
    );
  },

  ackThresholdAlert(body: ThresholdAlertAckRequest) {
    return apiClient.request<ThresholdAlertListItem>('/api/threshold-alert/ack', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },
};
