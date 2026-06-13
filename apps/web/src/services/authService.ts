import { apiClient } from './apiClient';
import type { AuthUser, LoginRequest } from '../types/auth';

export const authService = {
  login(payload: LoginRequest) {
    return apiClient.request<AuthUser>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
};

