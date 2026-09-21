export type Client = {
  id: string;
  organizationId: string;
  name: string;
  legalName?: string;
  status: "PROSPECT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
  website?: string;
  email?: string;
  phone?: string;
  notes?: string;
  archivedAt?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ClientContact = {
  id: string;
  clientId: string;
  name: string;
  email: string;
  phone?: string;
  jobTitle?: string;
  primary: boolean;
  archivedAt?: string;
  version: number;
};

export type ServiceDefinition = {
  id: string;
  code: string;
  name: string;
  description?: string;
  status: "ACTIVE" | "INACTIVE" | "ARCHIVED";
  archivedAt?: string;
  version: number;
};

export type Project = {
  id: string;
  clientId: string;
  clientName: string;
  clientStatus: string;
  serviceId: string;
  serviceName: string;
  serviceCode: string;
  name: string;
  description?: string;
  status: "DRAFT" | "ONBOARDING" | "READY" | "ACTIVE" | "ON_HOLD" | "COMPLETED" | "CANCELLED" | "ARCHIVED";
  valueMinor?: number;
  currencyCode?: string;
  targetStartDate?: string;
  previousStatus?: string;
  version: number;
};

export type WorkflowCondition = {
  field: "SERVICE_CODE" | "PROJECT_VALUE_MINOR" | "CLIENT_STATUS" | "CURRENCY_CODE";
  operator: "EQUALS" | "NOT_EQUALS" | "GREATER_THAN" | "GREATER_THAN_OR_EQUAL" | "IN";
  value: string;
};

export type WorkflowStep = {
  id: string;
  stepKey: string;
  name: string;
  description?: string;
  stepType: "WELCOME" | "INSTRUCTION" | "FORM" | "FILE_UPLOAD" | "PAYMENT" | "CONTRACT" | "PLATFORM_ACCESS" | "MANUAL_TASK" | "APPROVAL" | "EXTERNAL_LINK" | "VIDEO_GUIDE" | "MEETING" | "CUSTOM";
  displayOrder: number;
  required: boolean;
  blocking: boolean;
  clientVisible: boolean;
  requiresReview: boolean;
  dependencyMode: "NONE" | "ALL" | "ANY";
  condition?: WorkflowCondition;
  assignedRole?: string;
  dueAfterHours?: number;
  reminderPolicyId?: string;
  allowSkip: boolean;
  allowReopen: boolean;
  configuration: Record<string, unknown>;
  dependencyStepIds: string[];
  version: number;
};

export type WorkflowTemplate = {
  id: string;
  serviceId?: string;
  name: string;
  description?: string;
  status: "ACTIVE" | "ARCHIVED";
  archivedAt?: string;
  version: number;
};

export type TemplateVersion = {
  id: string;
  templateId: string;
  versionNumber: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  publishedAt?: string;
  version: number;
};

export type VersionBundle = { version: TemplateVersion; steps: WorkflowStep[] };
export type TemplateBundle = { template: WorkflowTemplate; draftVersion: TemplateVersion; steps: WorkflowStep[] };

export type OnboardingStep = WorkflowStep & {
  onboardingId: string;
  sourceStepId: string;
  dueAt?: string;
  applicable: boolean;
  status: "LOCKED" | "AVAILABLE" | "IN_PROGRESS" | "SUBMITTED" | "UNDER_REVIEW" | "NEEDS_REVISION" | "COMPLETED" | "SKIPPED" | "FAILED" | "CANCELLED";
  dependencyStepInstanceIds: string[];
  completedAt?: string;
};

export type OnboardingView = {
  onboarding: {
    id: string;
    projectId: string;
    sourceTemplateId: string;
    sourceTemplateVersionId: string;
    snapshotVersionNumber: number;
    status: string;
    ready: boolean;
    version: number;
  };
  steps: OnboardingStep[];
  progress: number;
};
