import { apiClient } from './apiClient';
import type {
  BoxplotDataRequest,
  BoxplotDataResponse,
  CorrelationHeatmapDataRequest,
  CorrelationHeatmapDataResponse,
  DataExplorationDatasetOption,
  HistogramDataRequest,
  HistogramDataResponse,
  HistogramFieldListData,
  ProcessFlowEquipmentOption,
  ProcessFlowRequest,
  ProcessFlowResponse,
  TimeseriesDataRequest,
  TimeseriesDataResponse,
} from '../types/dataExploration';

export const dataExplorationService = {
  getDatasets() {
    return apiClient.request<DataExplorationDatasetOption[]>('/api/data-exploration/datasets');
  },

  getEquipmentOptions() {
    return apiClient.request<ProcessFlowEquipmentOption[]>('/api/equipment/master?ai_only=false');
  },

  getHistogramFields(options?: { datasetKey?: string; from?: string; to?: string; equipmentId?: string }) {
    const queryParams = new URLSearchParams();
    if (options?.datasetKey && options.datasetKey.trim().length > 0) {
      queryParams.set('datasetKey', options.datasetKey.trim());
    }
    if (options?.from && options.from.trim().length > 0) {
      queryParams.set('from', options.from.trim());
    }
    if (options?.to && options.to.trim().length > 0) {
      queryParams.set('to', options.to.trim());
    }
    if (options?.equipmentId && options.equipmentId.trim().length > 0) {
      queryParams.set('equipmentId', options.equipmentId.trim());
    }

    const queryText = queryParams.toString();
    const path =
      queryText.length > 0
        ? `/api/data-exploration/histogram/fields?${queryText}`
        : '/api/data-exploration/histogram/fields';
    return apiClient.request<HistogramFieldListData>(path);
  },

  getHistogramData(request: HistogramDataRequest) {
    return apiClient.request<HistogramDataResponse>('/api/data-exploration/histogram/query', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  getTimeseriesFields(options?: { datasetKey?: string; from?: string; to?: string; equipmentId?: string }) {
    const queryParams = new URLSearchParams();
    if (options?.datasetKey && options.datasetKey.trim().length > 0) {
      queryParams.set('datasetKey', options.datasetKey.trim());
    }
    if (options?.from && options.from.trim().length > 0) {
      queryParams.set('from', options.from.trim());
    }
    if (options?.to && options.to.trim().length > 0) {
      queryParams.set('to', options.to.trim());
    }
    if (options?.equipmentId && options.equipmentId.trim().length > 0) {
      queryParams.set('equipmentId', options.equipmentId.trim());
    }

    const queryText = queryParams.toString();
    const path =
      queryText.length > 0
        ? `/api/data-exploration/timeseries/fields?${queryText}`
        : '/api/data-exploration/timeseries/fields';
    return apiClient.request<HistogramFieldListData>(path);
  },

  getTimeseriesData(request: TimeseriesDataRequest) {
    return apiClient.request<TimeseriesDataResponse>('/api/data-exploration/timeseries/query', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  getCorrelationHeatmapFields(options?: {
    datasetKey?: string;
    from?: string;
    to?: string;
    equipmentId?: string;
  }) {
    const queryParams = new URLSearchParams();
    if (options?.datasetKey && options.datasetKey.trim().length > 0) {
      queryParams.set('datasetKey', options.datasetKey.trim());
    }
    if (options?.from && options.from.trim().length > 0) {
      queryParams.set('from', options.from.trim());
    }
    if (options?.to && options.to.trim().length > 0) {
      queryParams.set('to', options.to.trim());
    }
    if (options?.equipmentId && options.equipmentId.trim().length > 0) {
      queryParams.set('equipmentId', options.equipmentId.trim());
    }

    const queryText = queryParams.toString();
    const path =
      queryText.length > 0
        ? `/api/data-exploration/correlation-heatmap/fields?${queryText}`
        : '/api/data-exploration/correlation-heatmap/fields';
    return apiClient.request<HistogramFieldListData>(path);
  },

  getCorrelationHeatmapData(request: CorrelationHeatmapDataRequest) {
    return apiClient.request<CorrelationHeatmapDataResponse>(
      '/api/data-exploration/correlation-heatmap/query',
      {
        method: 'POST',
        body: JSON.stringify(request),
      },
    );
  },

  getBoxplotFields(options?: { datasetKey?: string; from?: string; to?: string; equipmentId?: string }) {
    const queryParams = new URLSearchParams();
    if (options?.datasetKey && options.datasetKey.trim().length > 0) {
      queryParams.set('datasetKey', options.datasetKey.trim());
    }
    if (options?.from && options.from.trim().length > 0) {
      queryParams.set('from', options.from.trim());
    }
    if (options?.to && options.to.trim().length > 0) {
      queryParams.set('to', options.to.trim());
    }
    if (options?.equipmentId && options.equipmentId.trim().length > 0) {
      queryParams.set('equipmentId', options.equipmentId.trim());
    }

    const queryText = queryParams.toString();
    const path =
      queryText.length > 0
        ? `/api/data-exploration/boxplot/fields?${queryText}`
        : '/api/data-exploration/boxplot/fields';
    return apiClient.request<HistogramFieldListData>(path);
  },

  getBoxplotData(request: BoxplotDataRequest) {
    return apiClient.request<BoxplotDataResponse>('/api/data-exploration/boxplot/query', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  getProcessFlow(request: ProcessFlowRequest, options?: { signal?: AbortSignal }) {
    const queryParams = new URLSearchParams();
    if (request.datasetKey && request.datasetKey.trim().length > 0) {
      queryParams.set('datasetKey', request.datasetKey.trim());
    }
    if (request.mccode && request.mccode.trim().length > 0) {
      queryParams.set('mccode', request.mccode.trim());
    }
    queryParams.set('start', request.start);
    queryParams.set('end', request.end);
    if (request.opstats && request.opstats.length > 0) {
      queryParams.set('opstats', request.opstats.join(','));
    }
    if (request.fields && request.fields.length > 0) {
      queryParams.set('fields', request.fields.join(','));
    }
    if (request.limit != null) {
      queryParams.set('limit', String(request.limit));
    }
    if (request.autoRefresh != null) {
      queryParams.set('autoRefresh', String(request.autoRefresh));
    }
    return apiClient.request<ProcessFlowResponse>(
      `/api/data-exploration/processflow?${queryParams.toString()}`,
      {
        signal: options?.signal,
      },
    );
  },
};
