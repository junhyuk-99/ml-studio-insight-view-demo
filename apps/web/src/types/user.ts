import type { UserRole, UserUseflag } from './auth';

export type UserItem = {
  empcode: string;
  empname: string;
  role: UserRole;
  useflag: UserUseflag;
};

export type CreateUserRequest = {
  empcode: string;
  empname: string;
  emppass: string;
  role: UserRole;
  useflag: UserUseflag;
};

export type UpdateUserRequest = {
  empname: string;
  role: UserRole;
  useflag: UserUseflag;
};

export type ChangePasswordRequest = {
  currentPassword: string;
  newPassword: string;
};
