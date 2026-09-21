import { apiRequest } from "@/lib/api-client";
import type { AuthUser, LoginResult } from "./types";

const json = (body: unknown): RequestInit => ({
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const authApi = {
  login: (email: string, password: string, organizationSlug: string) =>
    apiRequest<LoginResult>("/api/v1/auth/login", json({ email, password, organizationSlug })),
  completeMfa: (challengeToken: string, code: string) =>
    apiRequest<LoginResult>("/api/v1/auth/mfa/complete", json({ challengeToken, code })),
  forgot: (email: string, organizationSlug: string) =>
    apiRequest<{ message: string }>("/api/v1/auth/forgot-password", json({ email, organizationSlug })),
  reset: (token: string, password: string) =>
    apiRequest<{ message: string }>("/api/v1/auth/reset-password", json({ token, password })),
  verify: (token: string, password: string) =>
    apiRequest<{ message: string }>("/api/v1/auth/verify-email", json({ token, password })),
  acceptInvitation: (token: string, password: string) =>
    apiRequest<{ message: string }>("/api/v1/auth/accept-invitation", json({ token, password })),
  me: () => apiRequest<AuthUser>("/api/v1/auth/me"),
  logout: () => apiRequest<{ message: string }>("/api/v1/auth/logout", json({})),
};
