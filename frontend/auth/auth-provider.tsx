"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { apiRequest, apiRequestWithMeta, ApiClientError } from "@/services/api-client";
import type { AuthResponse, AuthUser } from "@/types/auth";
import type { ApiResult } from "@/types/api";

type AuthStatus = "loading" | "authenticated" | "anonymous";

type AuthContextValue = {
  status: AuthStatus;
  user: AuthUser | null;
  accessToken: string | null;
  login: (email: string, organizationSlug: string, password: string) => Promise<AuthResponse>;
  completeAuth: (response: AuthResponse) => void;
  logout: () => Promise<void>;
  authorizedRequest: <T>(path: string, init?: RequestInit) => Promise<T>;
  authorizedRequestWithMeta: <T, M = Record<string, unknown>>(path: string, init?: RequestInit) => Promise<ApiResult<T, M>>;
  hasPermission: (permission: string) => boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const refreshInFlight = useRef<Promise<AuthResponse> | null>(null);

  const clearAuth = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setStatus("anonymous");
  }, []);

  const applyAuth = useCallback((response: AuthResponse) => {
    if (response.accessToken && response.user) {
      setAccessToken(response.accessToken);
      setUser(response.user);
      setStatus("authenticated");
    }
  }, []);

  const refreshSession = useCallback(async () => {
    if (!refreshInFlight.current) {
      refreshInFlight.current = apiRequest<AuthResponse>("/auth/refresh", { method: "POST" })
        .then((response) => {
          if (!response.accessToken || !response.user) {
            throw new ApiClientError("The session could not be refreshed.", 401, "INVALID_SESSION");
          }
          applyAuth(response);
          return response;
        })
        .finally(() => {
          refreshInFlight.current = null;
        });
    }
    return refreshInFlight.current;
  }, [applyAuth]);

  useEffect(() => {
    // The root provider also wraps the client portal. Keep the two refresh-cookie domains fully
    // separate and do not generate internal-auth traffic while a client is using /portal.
    if (window.location.pathname.startsWith("/portal")) {
      clearAuth();
      return;
    }
    let active = true;
    refreshSession().catch(() => { if (active) clearAuth(); });
    return () => { active = false; };
  }, [clearAuth, refreshSession]);

  const login = useCallback(async (email: string, organizationSlug: string, password: string) => {
    const response = await apiRequest<AuthResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, organizationSlug, password }),
    });
    applyAuth(response);
    return response;
  }, [applyAuth]);

  const logout = useCallback(async () => {
    try {
      await apiRequest("/auth/logout", { method: "POST" });
    } finally {
      clearAuth();
    }
  }, [clearAuth]);

  const authorizedRequest = useCallback(async <T,>(path: string, init?: RequestInit) => {
    if (!accessToken) throw new ApiClientError("Authentication is required.", 401, "AUTHENTICATION_REQUIRED");
    try {
      return await apiRequest<T>(path, init, accessToken);
    } catch (error) {
      if (!(error instanceof ApiClientError) || error.status !== 401) throw error;
      try {
        const refreshed = await refreshSession();
        return await apiRequest<T>(path, init, refreshed.accessToken);
      } catch (refreshError) {
        clearAuth();
        throw refreshError;
      }
    }
  }, [accessToken, clearAuth, refreshSession]);

  const authorizedRequestWithMeta = useCallback(async <T, M = Record<string, unknown>>(path: string, init?: RequestInit) => {
    if (!accessToken) throw new ApiClientError("Authentication is required.", 401, "AUTHENTICATION_REQUIRED");
    try {
      return await apiRequestWithMeta<T, M>(path, init, accessToken);
    } catch (error) {
      if (!(error instanceof ApiClientError) || error.status !== 401) throw error;
      try {
        const refreshed = await refreshSession();
        return await apiRequestWithMeta<T, M>(path, init, refreshed.accessToken);
      } catch (refreshError) {
        clearAuth();
        throw refreshError;
      }
    }
  }, [accessToken, clearAuth, refreshSession]);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    user,
    accessToken,
    login,
    completeAuth: applyAuth,
    logout,
    authorizedRequest,
    authorizedRequestWithMeta,
    hasPermission: (permission) => Boolean(user?.permissions.includes(permission)),
  }), [status, user, accessToken, login, applyAuth, logout, authorizedRequest, authorizedRequestWithMeta]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
