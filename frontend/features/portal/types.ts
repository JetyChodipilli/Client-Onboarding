export type PublicInvitation = {
  status: "VALID" | "EXPIRED" | "REVOKED" | "ACCEPTED" | "INVALID";
  organizationName: string;
  organizationSlug: string;
  clientName: string;
  projectName: string;
  contactName: string;
  email: string;
  expiresAt: string;
};

export type ClientUser = {
  id: string;
  organizationId: string;
  organizationName: string;
  organizationSlug: string;
  clientId: string;
  clientName: string;
  email: string;
  displayName: string;
  role: "ADMIN" | "MEMBER";
  permissions: string[];
  sessionId: string;
};

export type PortalProject = {
  id: string;
  name: string;
  projectStatus: string;
  clientName: string;
  onboardingStatus: string;
  progress: number;
  pendingRequirements: number;
};

export type PortalStep = {
  id: string;
  name: string;
  description?: string;
  type: string;
  status: string;
  required: boolean;
  blocking: boolean;
  deadline?: string;
  waitingFor: "YOUR_ACTION" | "OUR_TEAM" | "NONE";
  blockingReason?: string;
  actionable: boolean;
  version: number;
};

export type PortalDashboard = {
  projectId: string;
  projectName: string;
  projectStatus: string;
  clientName: string;
  onboardingId: string;
  onboardingStatus: string;
  currentStatus: string;
  progress: number;
  nextAction?: PortalStep;
  waitingFor: "YOU" | "OUR_TEAM" | "NONE";
  blockingReason?: string;
  helpEmail?: string;
  availableHelp: string;
  steps: PortalStep[];
};
