export const fieldTypes = ["TEXT", "TEXTAREA", "NUMBER", "EMAIL", "URL", "DATE", "DROPDOWN", "RADIO", "CHECKBOX", "MULTI_SELECT", "BOOLEAN"] as const;
export type Answer = string | number | boolean | string[];
export type Answers = Record<string, Answer>;
export type FormField = {
  key: string; label: string; type: typeof fieldTypes[number]; required: boolean; helpText?: string;
  options: string[]; maxLength?: number; min?: number; max?: number;
  condition?: { fieldKey: string; operator: "EQUALS" | "NOT_EQUALS" | "CONTAINS"; value: string };
};
export type FormTemplate = { id: string; name: string; description?: string; archivedAt?: string; version: number };
export type FormDefinition = { id: string; formId: string; versionNumber: number; status: "DRAFT" | "PUBLISHED"; fields: FormField[]; version: number };
export type FormResponse = { id?: string; stepId: string; formVersionId: string; status: "DRAFT" | "SUBMITTED" | "UNDER_REVIEW" | "NEEDS_REVISION" | "APPROVED"; answers: Answers; submissionNumber: number; reviewNote?: string; updatedAt?: string; version: number };
export type FormView = { formName: string; projectId: string; stepName: string; stepStatus: string; projectStatus: string; onboardingStatus: string; deadline?: string; allowSkip: boolean; allowReopen: boolean; definition: FormDefinition; response: FormResponse };
export type Submission = { id: string; submissionNumber: number; answers: Answers; createdAt: string; reviews: { decision: string; note?: string; createdAt: string }[] };

export function visibleFields(fields: FormField[], answers: Answers): FormField[] {
  const visible: Answers = {};
  return fields.filter((field) => {
    const c = field.condition;
    const value = c && Object.hasOwn(visible, c.fieldKey) ? visible[c.fieldKey] : undefined;
    const shown = !c || value !== undefined && (c.operator === "CONTAINS" ? Array.isArray(value) && value.includes(c.value) : c.operator === "EQUALS" ? String(value) === c.value : String(value) !== c.value);
    if (shown && answers[field.key] !== undefined && answers[field.key] !== "" && (!Array.isArray(answers[field.key]) || (answers[field.key] as string[]).length > 0)) visible[field.key] = answers[field.key];
    return shown;
  });
}
