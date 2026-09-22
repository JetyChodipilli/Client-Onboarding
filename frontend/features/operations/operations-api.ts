import { apiRequest } from "@/lib/api-client";
import type {
  Client,
  ClientContact,
  ClientInvitation,
  OnboardingStep,
  OnboardingView,
  Project,
  ServiceDefinition,
  TemplateBundle,
  TemplateVersion,
  VersionBundle,
  WorkflowStep,
  WorkflowTemplate,
} from "./types";

const json = (method: string, body: unknown, headers: Record<string, string> = {}): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json", ...headers },
  body: JSON.stringify(body),
});

export const operationsApi = {
  clients: (search = "") => apiRequest<Client[]>(`/api/v1/clients?search=${encodeURIComponent(search)}&page=0&size=50`),
  client: (id: string) => apiRequest<Client>(`/api/v1/clients/${id}`),
  createClient: (body: Partial<Client> & { name: string; status: string }) => apiRequest<Client>("/api/v1/clients", json("POST", { ...body, version: 0 })),
  contacts: (clientId: string) => apiRequest<ClientContact[]>(`/api/v1/clients/${clientId}/contacts`),
  createContact: (clientId: string, body: { name: string; email: string; jobTitle?: string; primary: boolean }) => apiRequest<ClientContact>(`/api/v1/clients/${clientId}/contacts`, json("POST", { ...body, version: 0 })),

  services: () => apiRequest<ServiceDefinition[]>("/api/v1/services?page=0&size=100"),
  createService: (body: { code: string; name: string; description?: string; status: string }) => apiRequest<ServiceDefinition>("/api/v1/services", json("POST", { ...body, version: 0 })),

  projects: () => apiRequest<Project[]>("/api/v1/projects?page=0&size=50"),
  project: (id: string) => apiRequest<Project>(`/api/v1/projects/${id}`),
  createProject: (body: { clientId: string; serviceId: string; name: string; description?: string; valueMinor?: number; currencyCode?: string; targetStartDate?: string }) => apiRequest<Project>("/api/v1/projects", json("POST", { ...body, version: 0 })),

  templates: () => apiRequest<WorkflowTemplate[]>("/api/v1/workflow-templates?page=0&size=100"),
  template: (id: string) => apiRequest<WorkflowTemplate>(`/api/v1/workflow-templates/${id}`),
  createTemplate: (body: { name: string; description?: string; serviceId?: string }) => apiRequest<TemplateBundle>("/api/v1/workflow-templates", json("POST", body)),
  versions: (templateId: string) => apiRequest<TemplateVersion[]>(`/api/v1/workflow-templates/${templateId}/versions`),
  version: (versionId: string) => apiRequest<VersionBundle>(`/api/v1/workflow-template-versions/${versionId}`),
  createVersion: (templateId: string, sourceVersionId?: string) => apiRequest<VersionBundle>(`/api/v1/workflow-templates/${templateId}/versions`, json("POST", { sourceVersionId })),
  replaceSteps: (versionId: string, version: number, steps: WorkflowStep[]) => apiRequest<VersionBundle>(`/api/v1/workflow-template-versions/${versionId}/steps`, json("PUT", { version, steps })),
  publish: (versionId: string, version: number) => apiRequest<VersionBundle>(`/api/v1/workflow-template-versions/${versionId}/publish?version=${version}`, { method: "POST" }),

  onboardingForProject: (projectId: string) => apiRequest<OnboardingView>(`/api/v1/projects/${projectId}/onboarding`),
  startOnboarding: (projectId: string, templateVersionId: string, projectVersion: number, idempotencyKey: string) => apiRequest<OnboardingView>(`/api/v1/projects/${projectId}/onboarding`, json("POST", { templateVersionId, projectVersion }, { "Idempotency-Key": idempotencyKey })),
  transitionStep: (stepId: string, targetStatus: OnboardingStep["status"], version: number) => apiRequest<OnboardingView>(`/api/v1/onboarding-steps/${stepId}/transition`, json("POST", { targetStatus, version })),
  invitations: (onboardingId: string) => apiRequest<ClientInvitation[]>(`/api/v1/onboardings/${onboardingId}/client-invitations`),
  inviteClient: (onboardingId: string, contactId: string, role: ClientInvitation["role"], idempotencyKey: string) => apiRequest<ClientInvitation>(`/api/v1/onboardings/${onboardingId}/client-invitations`, json("POST", { contactId, role }, { "Idempotency-Key": idempotencyKey })),
  resendClientInvitation: (invitationId: string, version: number) => apiRequest<ClientInvitation>(`/api/v1/client-invitations/${invitationId}/resend`, json("POST", { version })),
  revokeClientInvitation: (invitationId: string, version: number) => apiRequest<ClientInvitation>(`/api/v1/client-invitations/${invitationId}/revoke`, json("POST", { version })),
};
