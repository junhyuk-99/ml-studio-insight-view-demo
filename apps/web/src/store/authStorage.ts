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

  const normalizedRole = user.role === 'admin' ? 'admin' : user.role === 'user' ? 'user' : null;
  if (!normalizedRole) {
    return null;
  }

  const normalizedUseflag = user.useflag === 'n' ? 'n' : 'y';

  return {
    empcode: user.empcode,
    empname: user.empname,
    role: normalizedRole,
    useflag: normalizedUseflag,
  };
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

