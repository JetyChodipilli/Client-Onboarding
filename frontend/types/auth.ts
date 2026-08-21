export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  organizationId: string;
  organizationName: string;
  organizationSlug: string;
  permissions: string[];
};

export type AuthResponse = {
  accessToken: string | null;
  accessTokenExpiresAt: string | null;
  user: AuthUser | null;
  mfaRequired: boolean;
  mfaSetupRequired: boolean;
  challengeToken: string | null;
};
