import { apiClient } from './apiClient';
import type {
  AlgorithmSelectionApplyData,
  AlgorithmSelectionApplyRequest,
  AlgorithmSelectionData,
} from '../types/algorithmSelection';

export const algorithmSelectionService = {
  getSelectionOptions(datasetKey?: string) {
    const normalizedDatasetKey = datasetKey?.trim();
    if (normalizedDatasetKey) {
      const encodedDatasetKey = encodeURIComponent(normalizedDatasetKey);
      return apiClient.request<AlgorithmSelectionData>(`/api/algorithms/selection?dataset_key=${encodedDatasetKey}`);
    }
    return apiClient.request<AlgorithmSelectionData>('/api/algorithms/selection');
  },

  applySelection(request: AlgorithmSelectionApplyRequest) {
    return apiClient.request<AlgorithmSelectionApplyData>('/api/algorithms/selection/apply', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },
};
