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

const DEMO_DATASET_KEY = 'DEMO_DATASET_MANUFACTURING_AI';
const DEMO_RAW_ROWS = [
  {
    dataset_key: DEMO_DATASET_KEY,
    PRDTIME: '2026-06-10T09:00:00Z',
    MCCODE: 'DEMO-MC-001',
    TEMP_PV: 741.2,
    PRESSURE_PV: 2.12,
    MOTOR_CURRENT_PV: 17.9,
    VIBRATION_RMS_PV: 0.19,
    OPSTAT: 'RUN',
  },
  {
    dataset_key: DEMO_DATASET_KEY,
    PRDTIME: '2026-06-10T09:01:00Z',
    MCCODE: 'DEMO-MC-002',
    TEMP_PV: 738.7,
    PRESSURE_PV: 2.09,
    MOTOR_CURRENT_PV: 17.4,
    VIBRATION_RMS_PV: 0.21,
    OPSTAT: 'RUN',
  },
];

const DEMO_FEATURE_ROWS = [
  {
    dataset_key: DEMO_DATASET_KEY,
    equipment_id: 'DEMO-MC-001',
    window_start: '2026-06-10T09:00:00Z',
    window_end: '2026-06-10T09:29:00Z',
    TEMP_PV_MEAN: 741.2,
    PRESSURE_PV_MEAN: 2.12,
    VIBRATION_RMS_PV_MEAN: 0.19,
    source: 'Synthetic demo fallback',
  },
  {
    dataset_key: DEMO_DATASET_KEY,
    equipment_id: 'DEMO-MC-002',
    window_start: '2026-06-10T09:30:00Z',
    window_end: '2026-06-10T09:59:00Z',
    TEMP_PV_MEAN: 738.7,
    PRESSURE_PV_MEAN: 2.09,
    VIBRATION_RMS_PV_MEAN: 0.21,
    source: 'Synthetic demo fallback',
  },
];

const FALLBACK_DATA_SOURCES: DataSourceListData = {
  dataTypes: [
    {
      typeCode: 'DATABASE',
      typeName: 'Database',
      sortNo: 1,
      details: [
        {
          dtlCode: 'MONGODB',
          dtlName: 'MongoDB',
          sortNo: 1,
          datasets: [
            {
              datasetKey: DEMO_DATASET_KEY,
              datasetName: 'Synthetic demo fallback',
              displayName: 'Demo Manufacturing AI Dataset',
              sourceCollection: 'THISHMIDATA',
              targetFeatureCollection: 'thisfeature',
              datasetPurpose: 'FEATURE_SOURCE',
              featureEnabled: true,
              labelField: null,
              sortNo: 1,
              equipmentGroup: 'DEMO-MC',
              equipmentGroupName: 'Demo Manufacturing Equipment',
            },
          ],
        },
      ],
    },
  ],
};

const FALLBACK_RAW_PREVIEW: RawDataPreviewData = {
  sourceCollection: 'THISHMIDATA',
  datasetKey: DEMO_DATASET_KEY,
  datasetName: 'Synthetic demo fallback',
  datasetDisplayName: 'Demo Manufacturing AI Dataset',
  availableColumns: ['dataset_key', 'PRDTIME', 'MCCODE', 'TEMP_PV', 'PRESSURE_PV', 'MOTOR_CURRENT_PV', 'VIBRATION_RMS_PV', 'OPSTAT'],
  metadataColumns: ['dataset_key', 'PRDTIME', 'MCCODE', 'OPSTAT'],
  numericColumns: ['TEMP_PV', 'PRESSURE_PV', 'MOTOR_CURRENT_PV', 'VIBRATION_RMS_PV'],
  columnLabels: {},
  datasetKeyColumns: ['dataset_key'],
  datasetKeys: [{ dataset_key: DEMO_DATASET_KEY }],
  rawRows: DEMO_RAW_ROWS,
};

const FALLBACK_FEATURE_PREVIEW: FeaturePreviewData = {
  datasetKey: DEMO_DATASET_KEY,
  availableColumns: ['dataset_key', 'equipment_id', 'window_start', 'window_end', 'TEMP_PV_MEAN', 'PRESSURE_PV_MEAN', 'VIBRATION_RMS_PV_MEAN', 'source'],
  featureRows: DEMO_FEATURE_ROWS,
};

export const preprocessService = {
  getDataSources() {
    return apiClient
      .request<DataSourceListData>('/api/preprocess/data-sources')
      .then((data) => (data.dataTypes?.length ? data : FALLBACK_DATA_SOURCES))
      .catch(() => FALLBACK_DATA_SOURCES);
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

    return apiClient
      .request<RawDataPreviewData>(`/api/preprocess/raw-preview?${queryParts.join('&')}`)
      .then((data) => (data.rawRows?.length ? data : FALLBACK_RAW_PREVIEW))
      .catch(() => FALLBACK_RAW_PREVIEW);
  },

  getPreprocessOptions() {
    return apiClient.request<PreprocessOptionData>('/api/preprocess/options');
  },

  generateFeatures(request: FeatureGenerationRequest) {
    return apiClient.request<FeatureGenerationData>('/api/preprocess/features/generate', {
      method: 'POST',
      body: JSON.stringify(request),
    }).catch(() => ({
      totalWindowCount: DEMO_FEATURE_ROWS.length,
      createdCount: 0,
      skippedCount: DEMO_FEATURE_ROWS.length,
    }));
  },

  getFeaturePreview(datasetKey: string, limit = 30, compact = true) {
    const safeLimit = Number.isFinite(limit) ? Math.min(Math.max(1, Math.floor(limit)), 50) : 30;
    const encodedDatasetKey = encodeURIComponent(datasetKey.trim());
    const compactQuery = compact ? 'compact=true' : 'compact=false';
    return apiClient.request<FeaturePreviewData>(
      `/api/preprocess/features?dataset_key=${encodedDatasetKey}&limit=${safeLimit}&${compactQuery}`,
    ).then((data) => (data.featureRows?.length ? data : FALLBACK_FEATURE_PREVIEW))
      .catch(() => FALLBACK_FEATURE_PREVIEW);
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
