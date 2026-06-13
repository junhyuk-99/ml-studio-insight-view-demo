import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';
import { authService } from '../services/authService';
import { authStorage } from './authStorage';
import type { AuthUser } from '../types/auth';

type AuthContextValue = {
  user: AuthUser | null;
  isAuthenticated: boolean;
  signIn: (empcode: string, emppass: string) => Promise<void>;
  signOut: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<AuthUser | null>(() => authStorage.loadUser());

  const signIn = useCallback(async (empcode: string, emppass: string) => {
    const authUser = await authService.login({ empcode, emppass });
    authStorage.saveUser(authUser);
    setUser(authUser);
  }, []);

  const signOut = useCallback(() => {
    authStorage.clearUser();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    isAuthenticated: user !== null,
    signIn,
    signOut,
  }), [signIn, signOut, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}

