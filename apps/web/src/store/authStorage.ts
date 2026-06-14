import type { AuthUser } from '../types/auth';

const AUTH_STORAGE_KEY = 'ml-studio.auth.user';

function toAuthUser(value: unknown): AuthUser | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const user = value as Partial<AuthUser>;
  if (!user.empcode || !user.empname || !user.role) {
    return null;
  }

  const normalizedRoleValue = typeof user.role === 'string' ? user.role.trim().toUpperCase() : '';
  const normalizedRole = normalizedRoleValue === 'ADMIN' ? 'ADMIN' : normalizedRoleValue === 'USER' ? 'USER' : null;
  if (!normalizedRole) {
    return null;
  }

  const normalizedUseflagValue = typeof user.useflag === 'string' ? user.useflag.trim().toLowerCase() : '';
  const normalizedUseflag = normalizedUseflagValue === 'n' ? 'n' : 'y';

  const authUser: AuthUser = {
    empcode: user.empcode,
    empname: user.empname,
    role: normalizedRole,
    useflag: normalizedUseflag,
  };

  if (typeof user.userId === 'string') {
    authUser.userId = user.userId;
  }

  if (typeof user.username === 'string') {
    authUser.username = user.username;
  }

  if (typeof user.name === 'string') {
    authUser.name = user.name;
  }

  const storedDemoToken = user['token'];
  if (typeof storedDemoToken === 'string') {
    authUser['token'] = storedDemoToken;
  }

  return authUser;
}

export const authStorage = {
  loadUser(): AuthUser | null {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) {
      return null;
    }

    try {
      return toAuthUser(JSON.parse(raw));
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      return null;
    }
  },

  saveUser(user: AuthUser) {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user));
  },

  clearUser() {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  },
};

