"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { apiRequest, ApiClientError } from "@/services/api-client";
import type { AuthResponse, AuthUser } from "@/types/auth";

type ClientAuthStatus = "loading" | "authenticated" | "anonymous";
type ClientAuthContextValue = {
  status: ClientAuthStatus;
  user: AuthUser | null;
  accessToken: string | null;
  login: (email: string, organizationSlug: string, password: string) => Promise<AuthResponse>;
  verifyMfa: (challengeToken: string, code: string) => Promise<AuthResponse>;
  completeAuth: (response: AuthResponse) => void;
  logout: () => Promise<void>;
  authorizedRequest: <T>(path: string, init?: RequestInit) => Promise<T>;
};

const ClientAuthContext = createContext<ClientAuthContextValue | null>(null);

export function ClientAuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<ClientAuthStatus>("loading");
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
      refreshInFlight.current = apiRequest<AuthResponse>("/client-auth/refresh", { method: "POST" })
        .then((response) => {
          if (!response.accessToken || !response.user) {
            throw new ApiClientError("The client session could not be refreshed.", 401, "INVALID_SESSION");
          }
          applyAuth(response);
          return response;
        })
        .finally(() => { refreshInFlight.current = null; });
    }
    return refreshInFlight.current;
  }, [applyAuth]);

  useEffect(() => {
    let active = true;
    refreshSession().catch(() => { if (active) clearAuth(); });
    return () => { active = false; };
  }, [clearAuth, refreshSession]);

  const login = useCallback(async (email: string, organizationSlug: string, password: string) => {
    const response = await apiRequest<AuthResponse>("/client-auth/login", {
      method: "POST",
      body: JSON.stringify({ email, organizationSlug, password }),
    });
    applyAuth(response);
    return response;
  }, [applyAuth]);

  const verifyMfa = useCallback(async (challengeToken: string, code: string) => {
    const response = await apiRequest<AuthResponse>("/client-auth/mfa/verify", {
      method: "POST",
      body: JSON.stringify({ challengeToken, code }),
    });
    applyAuth(response);
    return response;
  }, [applyAuth]);

  const logout = useCallback(async () => {
    try { await apiRequest("/client-auth/logout", { method: "POST" }); }
    finally { clearAuth(); }
  }, [clearAuth]);

  const authorizedRequest = useCallback(async <T,>(path: string, init?: RequestInit) => {
    if (!accessToken) throw new ApiClientError("Client authentication is required.", 401, "AUTHENTICATION_REQUIRED");
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

  const value = useMemo<ClientAuthContextValue>(() => ({
    status, user, accessToken, login, verifyMfa, completeAuth: applyAuth, logout, authorizedRequest,
  }), [status, user, accessToken, login, verifyMfa, applyAuth, logout, authorizedRequest]);

  return <ClientAuthContext.Provider value={value}>{children}</ClientAuthContext.Provider>;
}

export function useClientAuth() {
  const value = useContext(ClientAuthContext);
  if (!value) throw new Error("useClientAuth must be used inside ClientAuthProvider");
  return value;
}
