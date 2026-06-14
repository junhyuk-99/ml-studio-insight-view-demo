import { apiClient } from './apiClient';
import type {
  AlgorithmSelectionApplyData,
  AlgorithmSelectionApplyRequest,
  AlgorithmSelectionData,
} from '../types/algorithmSelection';

const DEMO_DATASET_KEY = 'DEMO_DATASET_MANUFACTURING_AI';
const FALLBACK_SELECTION: AlgorithmSelectionData = {
  algoTypes: [
    {
      algoTypeCd: 'ANOMALY_DETECTION',
      algoTypeNm: 'Anomaly Detection',
      desc: 'Synthetic demo fallback',
      sortOrd: 1,
    },
    {
      algoTypeCd: 'SUPERVISED_LEARNING',
      algoTypeNm: 'Supervised Learning',
      desc: 'Synthetic demo fallback',
      sortOrd: 2,
    },
  ],
  algorithmsByType: {
    ANOMALY_DETECTION: [
      { algoCd: 'ISOLATION_FOREST', algoNm: 'Isolation Forest', sortOrd: 1 },
      { algoCd: 'AUTOENCODER', algoNm: 'AutoEncoder', sortOrd: 2 },
    ],
    SUPERVISED_LEARNING: [
      { algoCd: 'RANDOM_FOREST', algoNm: 'Random Forest', sortOrd: 1 },
    ],
  },
  activeSelection: {
    dataset_key: DEMO_DATASET_KEY,
    active_policy_id: 'DEMO-POLICY-IF-DEFAULT',
    active_algo_code: 'ISOLATION_FOREST',
    active_algo_name: 'Isolation Forest',
    updated_at: 'Synthetic demo fallback',
  },
};

export const algorithmSelectionService = {
  getSelectionOptions(datasetKey?: string) {
    const normalizedDatasetKey = datasetKey?.trim();
    if (normalizedDatasetKey) {
      const encodedDatasetKey = encodeURIComponent(normalizedDatasetKey);
      return apiClient
        .request<AlgorithmSelectionData>(`/api/algorithms/selection?dataset_key=${encodedDatasetKey}`)
        .then((data) => (data.algoTypes?.length ? data : FALLBACK_SELECTION))
        .catch(() => FALLBACK_SELECTION);
    }
    return apiClient
      .request<AlgorithmSelectionData>('/api/algorithms/selection')
      .then((data) => (data.algoTypes?.length ? data : FALLBACK_SELECTION))
      .catch(() => FALLBACK_SELECTION);
  },

  applySelection(request: AlgorithmSelectionApplyRequest) {
    return apiClient.request<AlgorithmSelectionApplyData>('/api/algorithms/selection/apply', {
      method: 'POST',
      body: JSON.stringify(request),
    }).catch(() => ({
      dataset_key: request.dataset_key || DEMO_DATASET_KEY,
      active_policy_id: `DEMO-POLICY-${request.algo_code || 'IF'}-DEFAULT`,
      active_algo_code: request.algo_code || 'ISOLATION_FOREST',
      active_algo_name: request.algo_code || 'Isolation Forest',
      changed_by: request.changed_by ?? 'Synthetic demo fallback',
      changed_reason: request.changed_reason ?? 'Synthetic demo fallback',
      use_flag: 'Y',
      reg_date: null,
      updated_at: 'Synthetic demo fallback',
    }));
  },
};
