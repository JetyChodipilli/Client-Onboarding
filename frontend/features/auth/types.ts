export type AuthUser = {
  id: string;
  organizationId: string;
  organizationName?: string;
  organizationSlug?: string;
  email: string;
  displayName: string;
  role: string;
  permissions: string[];
  sessionId: string;
};

export type LoginResult = {
  state: "AUTHENTICATED" | "MFA_REQUIRED" | "MFA_ENROLLMENT_REQUIRED";
  challengeToken?: string;
  enrollmentSecret?: string;
  otpAuthUri?: string;
  recoveryCodes: string[];
  user: AuthUser;
};
