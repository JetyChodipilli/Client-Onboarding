import { apiRequest } from "@/lib/api-client";
import type { ClientUser, PortalDashboard, PortalProject, PublicInvitation } from "./types";

const post = (body: unknown): RequestInit => ({
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const portalApi = {
  inspect: (token: string) => apiRequest<PublicInvitation>("/api/v1/client-invitations/inspect", post({ token })),
  accept: (token: string, password: string) => apiRequest<{ message: string; organizationSlug: string; projectName: string }>("/api/v1/client-invitations/accept", post({ token, password })),
  login: (email: string, password: string, organizationSlug: string) => apiRequest<ClientUser>("/api/v1/client-auth/login", post({ email, password, organizationSlug })),
  forgot: (email: string, organizationSlug: string) => apiRequest<{ message: string }>("/api/v1/client-auth/forgot-password", post({ email, organizationSlug })),
  projects: () => apiRequest<PortalProject[]>("/api/v1/client-portal/projects"),
  dashboard: (projectId: string) => apiRequest<PortalDashboard>(`/api/v1/client-portal/projects/${projectId}`),
  transition: (projectId: string, stepId: string, targetStatus: "IN_PROGRESS" | "COMPLETED" | "SUBMITTED", version: number) => apiRequest<PortalDashboard>(`/api/v1/client-portal/projects/${projectId}/steps/${stepId}/transition`, post({ targetStatus, version })),
};
