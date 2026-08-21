export type WorkflowTemplateStatus = "ACTIVE" | "ARCHIVED";
export type WorkflowVersionStatus = "DRAFT" | "PUBLISHED";
export type WorkflowStepType = "WELCOME" | "INSTRUCTION" | "FORM" | "FILE_UPLOAD" | "PAYMENT" | "CONTRACT" | "PLATFORM_ACCESS" | "MANUAL_TASK" | "APPROVAL" | "EXTERNAL_LINK" | "VIDEO_GUIDE" | "MEETING" | "CUSTOM";
export type DependencyMode = "NONE" | "ALL" | "ANY";

export type WorkflowTemplateSummary = {
  id: string; name: string; description: string | null; status: WorkflowTemplateStatus;
  latestPublishedVersion: number | null; latestPublishedVersionId: string | null; draftVersion: number | null; updatedAt: string; version: number;
};
export type WorkflowVersionSummary = {
  id: string; versionNumber: number; status: WorkflowVersionStatus; changeNote: string | null;
  publishedAt: string | null; updatedAt: string; stepCount: number; version: number;
};
export type WorkflowStep = {
  id: string; stepKey: string; name: string; description: string | null; stepType: WorkflowStepType; displayOrder: number;
  required: boolean; blocking: boolean; clientVisible: boolean; requiresReview: boolean; dependencyMode: DependencyMode;
  conditionExpression: Record<string, unknown>; assignedRoleId: string | null; dueAfterHours: number | null; reminderPolicyId: string | null;
  allowSkip: boolean; allowReopen: boolean; configuration: Record<string, unknown>; dependencyKeys: string[];
};
export type WorkflowVersion = WorkflowVersionSummary & { templateId: string; steps: WorkflowStep[] };
export type WorkflowTemplate = {
  id: string; name: string; description: string | null; status: WorkflowTemplateStatus; archivedAt: string | null;
  createdAt: string; updatedAt: string; version: number; versions: WorkflowVersionSummary[];
};
export type WorkflowStepDraft = Omit<WorkflowStep, "id" | "conditionExpression" | "configuration" | "reminderPolicyId"> & {
  conditionExpression?: Record<string, unknown>; configuration?: Record<string, unknown>; reminderPolicyId?: null;
};
