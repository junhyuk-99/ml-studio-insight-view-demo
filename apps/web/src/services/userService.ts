import { apiClient } from './apiClient';
import type {
  ChangePasswordRequest,
  CreateUserRequest,
  UpdateUserRequest,
  UserItem,
} from '../types/user';

export const userService = {
  getUsers() {
    return apiClient.request<UserItem[]>('/api/users');
  },

  getUser(empcode: string) {
    const encodedEmpcode = encodeURIComponent(empcode);
    return apiClient.request<UserItem>(`/api/users/${encodedEmpcode}`);
  },

  createUser(payload: CreateUserRequest) {
    return apiClient.request<UserItem>('/api/users', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  updateUser(empcode: string, payload: UpdateUserRequest) {
    const encodedEmpcode = encodeURIComponent(empcode);
    return apiClient.request<UserItem>(`/api/users/${encodedEmpcode}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },

  deleteUser(empcode: string) {
    const encodedEmpcode = encodeURIComponent(empcode);
    return apiClient.request<void>(`/api/users/${encodedEmpcode}`, {
      method: 'DELETE',
    });
  },

  changePassword(empcode: string, payload: ChangePasswordRequest) {
    const encodedEmpcode = encodeURIComponent(empcode);
    return apiClient.request<void>(`/api/users/${encodedEmpcode}/password`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },
};
