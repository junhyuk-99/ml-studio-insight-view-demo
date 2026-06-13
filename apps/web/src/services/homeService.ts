import { apiClient } from './apiClient';
import type { HomeDashboardResponse } from '../types/homeDashboard';

export const homeService = {
  fetchDashboard() {
    return apiClient.request<HomeDashboardResponse>('/api/home/dashboard');
  },
};
