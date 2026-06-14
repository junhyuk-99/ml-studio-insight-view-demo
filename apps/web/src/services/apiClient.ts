import { ApiError, type ApiResponse } from '../types/api';
import { authStorage } from '../store/authStorage';

const DEFAULT_API_BASE_URL = import.meta.env.DEV
  ? 'http://localhost:8090'
  : `${window.location.protocol}//${window.location.hostname}:8090`;

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL;

function isDemoAuthSession(authUser: ReturnType<typeof authStorage.loadUser>): boolean {
  const demoMarker = authUser?.['token'];
  return authUser?.userId === 'DEMO-ADMIN' || authUser?.username === 'admin' || demoMarker === 'DEMO_ACCESS_TOKEN';
}

function normalizeDemoErrorMessage(message: string, authUser: ReturnType<typeof authStorage.loadUser>): string {
  if (isDemoAuthSession(authUser) && message === 'Authentication context is unavailable for this demo request.') {
    return 'Demo data is not available for this request.';
  }

  return message;
}

export const apiClient = {
  async request<T>(path: string, options?: RequestInit): Promise<T> {
    const authUser = authStorage.loadUser();
    const headers = new Headers(options?.headers);

    headers.set('Content-Type', 'application/json');

    if (authUser) {
      headers.set('X-Auth-Empcode', authUser.empcode);
      headers.set('X-Auth-Role', authUser.role);
    }

    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
    });

    const body = (await response.json()) as ApiResponse<T>;

    if (!response.ok || !body.ok) {
      const message = normalizeDemoErrorMessage(body.message || 'API request failed.', authUser);
      throw new ApiError(message, body.errorCode ?? null);
    }

    return body.data;
  },
};
