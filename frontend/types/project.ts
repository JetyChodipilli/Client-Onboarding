export type ProjectStatus =
  | "DRAFT"
  | "ONBOARDING"
  | "READY"
  | "ACTIVE"
  | "ON_HOLD"
  | "COMPLETED"
  | "CANCELLED"
  | "ARCHIVED";

export type Project = {
  id: string;
  clientId: string;
  clientName: string;
  serviceId: string;
  serviceName: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ProjectMember = {
  id: string;
  projectId: string;
  organizationUserId: string;
  responsibility: string | null;
  createdAt: string;
};
