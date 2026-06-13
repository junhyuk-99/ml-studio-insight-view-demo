export type LoginRequest = {
  empcode: string;
  emppass: string;
};

export type UserRole = 'admin' | 'user';

export type UserUseflag = 'y' | 'n';

export type AuthUser = {
  empcode: string;
  empname: string;
  role: UserRole;
  useflag: UserUseflag;
};

