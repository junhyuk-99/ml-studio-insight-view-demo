import { apiClient } from './apiClient';
import type {
  DataSourceListData,
  FeatureAutoJobStatus,
  FeatureAutoJobListData,
  FeatureAutoJobUpsertRequest,
  FeatureAutoTriggerData,
  FeatureAutoTriggerRequest,
  FeatureGenerationData,
  FeatureGenerationRequest,
  FeaturePreviewData,
  PreprocessOptionData,
  RawDataPreviewData,
} from '../types/preprocess';

export const preprocessService = {
  getDataSources() {
    return apiClient.request<DataSourceListData>('/api/preprocess/data-sources');
  },

  getRawPreview({
    typeCode,
    dtlCode,
    datasetKey,
    from,
    to,
    equipmentId,
    limit = 200,
  }: {
    typeCode?: string | null;
    dtlCode?: string | null;
    datasetKey?: string | null;
    from?: string | null;
    to?: string | null;
    equipmentId?: string | null;
    limit?: number;
  } = {}) {
    const safeLimit = Number.isFinite(limit) ? Math.min(Math.max(1, Math.floor(limit)), 200) : 200;
    const queryParts: string[] = [`limit=${safeLimit}`];

    if (typeof typeCode === 'string' && typeCode.trim().length > 0) {
      queryParts.push(`typeCode=${encodeURIComponent(typeCode.trim())}`);
    }

    if (typeof dtlCode === 'string' && dtlCode.trim().length > 0) {
      queryParts.push(`dtlCode=${encodeURIComponent(dtlCode.trim())}`);
    }

    if (typeof datasetKey === 'string' && datasetKey.trim().length > 0) {
      queryParts.push(`datasetKey=${encodeURIComponent(datasetKey.trim())}`);
    }

    if (typeof from === 'string' && from.trim().length > 0) {
      queryParts.push(`from=${encodeURIComponent(from.trim())}`);
    }

    if (typeof to === 'string' && to.trim().length > 0) {
      queryParts.push(`to=${encodeURIComponent(to.trim())}`);
    }

    if (typeof equipmentId === 'string' && equipmentId.trim().length > 0) {
      queryParts.push(`equipmentId=${encodeURIComponent(equipmentId.trim())}`);
    }

    return apiClient.request<RawDataPreviewData>(`/api/preprocess/raw-preview?${queryParts.join('&')}`);
  },

  getPreprocessOptions() {
    return apiClient.request<PreprocessOptionData>('/api/preprocess/options');
  },

  generateFeatures(request: FeatureGenerationRequest) {
    return apiClient.request<FeatureGenerationData>('/api/preprocess/features/generate', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  getFeaturePreview(datasetKey: string, limit = 30, compact = true) {
    const safeLimit = Number.isFinite(limit) ? Math.min(Math.max(1, Math.floor(limit)), 50) : 30;
    const encodedDatasetKey = encodeURIComponent(datasetKey.trim());
    const compactQuery = compact ? 'compact=true' : 'compact=false';
    return apiClient.request<FeaturePreviewData>(
      `/api/preprocess/features?dataset_key=${encodedDatasetKey}&limit=${safeLimit}&${compactQuery}`,
    );
  },

  getFeatureAutoJobs() {
    return apiClient.request<FeatureAutoJobListData>('/api/preprocess/feature-auto/jobs');
  },

  upsertFeatureAutoJob(request: FeatureAutoJobUpsertRequest) {
    return apiClient.request<FeatureAutoJobStatus>('/api/preprocess/feature-auto/jobs', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  triggerFeatureAutoJobs(request?: FeatureAutoTriggerRequest) {
    return apiClient.request<FeatureAutoTriggerData>('/api/preprocess/feature-auto/jobs/trigger', {
      method: 'POST',
      body: JSON.stringify(request ?? {}),
    });
  },
};
