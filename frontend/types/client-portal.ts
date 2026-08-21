import type { AuthResponse, AuthUser } from "@/types/auth";
import type { OnboardingStatus, OnboardingStepStatus } from "@/types/onboarding";
import type { ProjectStatus } from "@/types/project";
import type { WorkflowStepType } from "@/types/workflow";

export type ClientAuthUser = AuthUser;
export type ClientAuthResponse = AuthResponse;
export type ClientAccessLevel = "CLIENT_ADMIN" | "CLIENT_MEMBER";
export type ClientInvitationStatus = "PENDING" | "ACCEPTED" | "REVOKED" | "EXPIRED";

export type ClientInvitation = {
  id: string;
  onboardingId: string;
  projectId: string;
  clientId: string;
  contactId: string;
  email: string;
  displayName: string;
  accessLevel: ClientAccessLevel;
  status: ClientInvitationStatus;
  expiresAt: string;
  lastSentAt: string;
  resendCount: number;
  acceptedAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  version: number;
};

export type InvitationContact = { id: string; displayName: string; email: string };

export type ClientInvitationPreview = {
  invitationId: string;
  organizationName: string;
  organizationSlug: string;
  projectName: string;
  contactName: string;
  email: string;
  accessLevel: ClientAccessLevel;
  expiresAt: string;
  existingAccount: boolean;
};

export type ClientPortalNextAction = {
  projectId: string;
  stepId: string;
  title: string;
  description: string;
  dueAt: string | null;
  actionType: string;
};

export type ClientPortalProjectSummary = {
  projectId: string;
  projectName: string;
  clientName: string;
  serviceName: string;
  projectStatus: ProjectStatus;
  onboardingId: string | null;
  onboardingStatus: OnboardingStatus | null;
  progressPercent: number;
  completedRequirements: number;
  totalRequirements: number;
  waitingFor: "YOU" | "OUR_TEAM" | "PAUSED" | "COMPLETE" | "CANCELLED" | "EXPIRED";
  nextAction: ClientPortalNextAction | null;
  nearestDueAt: string | null;
};

export type ClientPortalDashboard = {
  organizationName: string;
  nextAction: ClientPortalNextAction | null;
  projects: ClientPortalProjectSummary[];
};

export type ClientPortalStep = {
  id: string;
  stepKey: string;
  name: string;
  description: string | null;
  stepType: WorkflowStepType;
  status: OnboardingStepStatus;
  required: boolean;
  blocking: boolean;
  requiresReview: boolean;
  dueAt: string | null;
  lockedReason: string | null;
  actionLabel: string | null;
};

export type ClientPortalProjectDetail = {
  projectId: string;
  projectName: string;
  clientName: string;
  serviceName: string;
  projectStatus: ProjectStatus;
  onboardingId: string | null;
  onboardingStatus: OnboardingStatus | null;
  progressPercent: number;
  completedRequirements: number;
  totalRequirements: number;
  waitingFor: string;
  nextAction: ClientPortalNextAction | null;
  blockingReason: string;
  helpText: string;
  yourAction: ClientPortalStep[];
  waitingForOurTeam: ClientPortalStep[];
  locked: ClientPortalStep[];
  completed: ClientPortalStep[];
};
