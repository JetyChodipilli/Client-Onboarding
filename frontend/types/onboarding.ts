import type { DependencyMode, WorkflowStepType } from "@/types/workflow";
export type OnboardingStatus = "DRAFT" | "INVITED" | "IN_PROGRESS" | "AWAITING_INTERNAL_REVIEW" | "NEEDS_REVISION" | "APPROVED" | "COMPLETED" | "PAUSED" | "EXPIRED" | "CANCELLED";
export type OnboardingStepStatus = "LOCKED" | "AVAILABLE" | "IN_PROGRESS" | "SUBMITTED" | "UNDER_REVIEW" | "NEEDS_REVISION" | "COMPLETED" | "SKIPPED" | "FAILED" | "CANCELLED";
export type OnboardingStep = {
  id: string; stepKey: string; name: string; description: string | null; stepType: WorkflowStepType; displayOrder: number;
  required: boolean; blocking: boolean; clientVisible: boolean; requiresReview: boolean; dependencyMode: DependencyMode; dependencyKeys: string[];
  conditionExpression: Record<string, unknown>; assignedRoleId: string | null; dueAt: string | null; reminderPolicyId: string | null;
  allowSkip: boolean; allowReopen: boolean; configuration: Record<string, unknown>; status: OnboardingStepStatus; completedAt: string | null; version: number;
};
export type Onboarding = {
  id: string; projectId: string; templateId: string; templateVersionId: string; templateName: string; templateVersionNumber: number;
  status: OnboardingStatus; ready: boolean; progressPercent: number; completedApplicableSteps: number; totalApplicableSteps: number;
  startedAt: string; completedAt: string | null; updatedAt: string; version: number; steps: OnboardingStep[];
};
