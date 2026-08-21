export type PlatformAccessTypeStatus = "ACTIVE" | "ARCHIVED";
export type PlatformAccessGuideVersionStatus = "DRAFT" | "PUBLISHED";
export type PlatformAccessRequestStatus = "NOT_STARTED" | "REQUESTED" | "CLIENT_SUBMITTED" | "UNDER_VERIFICATION" | "VERIFIED" | "NEEDS_REVISION" | "WAIVED";
export type PlatformAccessReviewAction = "START_VERIFICATION" | "VERIFY" | "REQUEST_REVISION" | "WAIVE";

export interface PlatformAccessGuideResource { type: "LINK" | "VIDEO" | "DOCUMENT"; label: string; url: string }
export interface PlatformAccessGuide {
  id: string; accessTypeId: string; versionNumber: number; status: PlatformAccessGuideVersionStatus; changeNote: string | null;
  instructionsMarkdown: string; helpUrl: string | null; resources: PlatformAccessGuideResource[]; publishedAt: string | null; updatedAt: string; version: number;
}
export interface PlatformAccessTypeSummary { id: string; code: string; name: string; description: string | null; status: PlatformAccessTypeStatus; publishedVersion: number | null; updatedAt: string; version: number }
export interface PlatformAccessTypeDetail { id: string; code: string; name: string; description: string | null; status: PlatformAccessTypeStatus; updatedAt: string; version: number; versions: PlatformAccessGuide[] }
export interface PublishedPlatformAccessGuide { accessTypeId: string; code: string; name: string; versionId: string; versionNumber: number }
export interface PlatformAccessReview { id: string; action: PlatformAccessReviewAction; reason: string | null; createdAt: string; createdBy: string }
export interface PlatformAccessQueueItem { id: string; projectId: string; onboardingId: string; stepId: string; accessTypeCode: string; accessTypeName: string; status: PlatformAccessRequestStatus; accountIdentifier: string | null; submittedAt: string | null; updatedAt: string; version: number }
export interface PlatformAccessRequestDetail {
  id: string; projectId: string; onboardingId: string; stepId: string; stepName: string; accessTypeCode: string; accessTypeName: string; description: string | null;
  guideVersion: number; instructions: string; helpUrl: string | null; resources: PlatformAccessGuideResource[]; status: PlatformAccessRequestStatus;
  accountIdentifier: string | null; clientNote: string | null; submittedAt: string | null; verificationStartedAt: string | null; verifiedAt: string | null;
  revisionRequestedAt: string | null; waivedAt: string | null; version: number; reviews: PlatformAccessReview[];
}
