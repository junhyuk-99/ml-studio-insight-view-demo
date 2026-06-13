import { apiClient } from './apiClient';
import type { UserRole } from '../types/auth';
import type { MenuResponse } from '../types/menu';

export const menuService = {
  getMenu(role: UserRole) {
    const encodedRole = encodeURIComponent(role);
    return apiClient.request<MenuResponse>(`/api/menu?role=${encodedRole}`);
  },
};

