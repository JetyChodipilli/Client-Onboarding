export type FormStatus = "ACTIVE" | "ARCHIVED";
export type FormVersionStatus = "DRAFT" | "PUBLISHED";
export type FormFieldType = "TEXT" | "TEXTAREA" | "NUMBER" | "EMAIL" | "URL" | "DATE" | "DROPDOWN" | "RADIO" | "CHECKBOX" | "MULTI_SELECT" | "FILE" | "BOOLEAN";
export type FormSubmissionStatus = "DRAFT" | "SUBMITTED" | "UNDER_REVIEW" | "NEEDS_REVISION" | "APPROVED";

export type FormField = {
  id: string;
  fieldKey: string;
  label: string;
  helpText: string | null;
  fieldType: FormFieldType;
  displayOrder: number;
  required: boolean;
  conditionExpression: Record<string, unknown>;
  configuration: Record<string, unknown>;
};
export type FormVersionSummary = {
  id: string; versionNumber: number; status: FormVersionStatus; changeNote: string | null;
  publishedAt: string | null; updatedAt: string; fieldCount: number; version: number;
};
export type FormVersion = FormVersionSummary & { formId: string; fields: FormField[] };
export type FormSummary = {
  id: string; name: string; description: string | null; status: FormStatus;
  latestPublishedVersion: number | null; latestPublishedVersionId: string | null; draftVersion: number | null;
  updatedAt: string; version: number;
};
export type FormTemplate = {
  id: string; name: string; description: string | null; status: FormStatus; archivedAt: string | null;
  createdAt: string; updatedAt: string; version: number; versions: FormVersionSummary[];
};
export type FormSubmission = {
  id: string; onboardingId: string; stepId: string; projectId: string; formVersionId: string; submissionNumber: number;
  previousSubmissionId: string | null; status: FormSubmissionStatus; submittedAt: string | null; reviewStartedAt: string | null;
  reviewedAt: string | null; revisionNote: string | null; updatedAt: string; version: number; answers: Record<string, unknown>;
};
export type ClientFormStep = {
  stepId: string; projectId: string; stepName: string; stepDescription: string | null; stepStatus: string; requiresReview: boolean;
  formId: string; formName: string; formVersionId: string; formVersionNumber: number; fields: FormField[];
  currentSubmission: FormSubmission | null; history: FormSubmission[];
};
export type FormReviewQueueItem = {
  submissionId: string; projectId: string; onboardingId: string; stepId: string; stepName: string; formId: string; formName: string;
  submissionNumber: number; status: FormSubmissionStatus; submittedAt: string | null; updatedAt: string; version: number;
};

export type PublishedFormVersion = { formId: string; formName: string; versionId: string; versionNumber: number; fieldCount: number };
