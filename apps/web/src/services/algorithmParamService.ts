import type {
  AlgorithmParamItem,
  AlgorithmParamSaveRequest,
  AlgorithmParamSaveResponse,
} from '../types/algorithmParam';
import { apiClient } from './apiClient';

export const algorithmParamService = {
  getParams(algoCd: string) {
    const encodedAlgoCd = encodeURIComponent(algoCd.trim());
    return apiClient.request<AlgorithmParamItem[]>(`/api/algorithm/params?algoCd=${encodedAlgoCd}`);
  },

  saveParams(request: AlgorithmParamSaveRequest) {
    return apiClient.request<AlgorithmParamSaveResponse>('/api/algorithm/params', {
      method: 'PUT',
      body: JSON.stringify(request),
    });
  },
};
