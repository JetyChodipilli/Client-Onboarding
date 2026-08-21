export type ServiceStatus = "ACTIVE" | "ARCHIVED";

export type ServiceDefinition = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  status: ServiceStatus;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};
