import { apiClient } from './apiClient';
import type { AuthUser, LoginRequest } from '../types/auth';

const DEMO_LOGIN_ID = 'admin';
const DEMO_LOGIN_CODE = 'admin';

function isDemoLogin(payload: LoginRequest): boolean {
  return payload.empcode.trim().toLowerCase() === DEMO_LOGIN_ID && payload.emppass === DEMO_LOGIN_CODE;
}

function createDemoSession(): AuthUser {
  return {
    empcode: 'DEMO-ADMIN',
    empname: 'Demo Admin',
    role: 'ADMIN',
    useflag: 'y',
    userId: 'DEMO-ADMIN',
    username: 'admin',
    name: 'Demo Admin',
    token: 'DEMO_ACCESS_TOKEN',
  };
}

export const authService = {
  login(payload: LoginRequest) {
    if (isDemoLogin(payload)) {
      return Promise.resolve(createDemoSession());
    }

    return apiClient.request<AuthUser>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  loginWithDemoAccount() {
    return Promise.resolve(createDemoSession());
  },

  createDemoSession(): AuthUser {
    return createDemoSession();
  },
};

