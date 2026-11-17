import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getAuthToken, registerUnauthorizedHandler } from '../api/client';
import { login as apiLogin, logout as apiLogout } from '../api/auth';
import type { LoginRequest } from '../types/api';

interface AuthContextValue {
  isAuthenticated: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(getAuthToken());

  useEffect(() => {
    registerUnauthorizedHandler(() => {
      apiLogout();
      setToken(null);
    });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated: !!token,
      login: async (credentials) => {
        const res = await apiLogin(credentials);
        setToken(res.token);
      },
      logout: () => {
        apiLogout();
        setToken(null);
      },
    }),
    [token],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
