export type LoginRequest = {
  empcode: string;
  emppass: string;
};

export type UserRole = 'ADMIN' | 'USER' | 'admin' | 'user';

export type UserUseflag = 'y' | 'n';

export type AuthUser = {
  empcode: string;
  empname: string;
  role: UserRole;
  useflag: UserUseflag;
  userId?: string;
  username?: string;
  name?: string;
  token?: string;
};

