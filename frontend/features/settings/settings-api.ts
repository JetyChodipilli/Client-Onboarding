import { apiRequest } from "@/lib/api-client";
import type { AuditEntry, Member, Organization, Role } from "./types";

const json = (method: string, body: unknown): RequestInit => ({ method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });

export const settingsApi = {
  organization: (id: string) => apiRequest<Organization>(`/api/v1/organizations/${id}`),
  updateOrganization: (id: string, name: string, version: number) => apiRequest<Organization>(`/api/v1/organizations/${id}`, json("PATCH", { name, version })),
  roles: () => apiRequest<Role[]>("/api/v1/roles"),
  permissions: () => apiRequest<string[]>("/api/v1/permissions"),
  createRole: (name: string, description: string, permissions: string[]) => apiRequest<Role>("/api/v1/roles", json("POST", { name, description, permissions, version: 0 })),
  members: () => apiRequest<Member[]>("/api/v1/organization-members"),
  inviteMember: (email: string, displayName: string, roleId: string) => apiRequest<Member>("/api/v1/organization-members", json("POST", { email, displayName, roleId })),
  audit: () => apiRequest<AuditEntry[]>("/api/v1/audit-logs?page=0&size=50"),
};
