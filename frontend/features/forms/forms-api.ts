import { apiRequest } from "@/lib/api-client";
import type { Answers, FormDefinition, FormField, FormTemplate, FormView, Submission } from "./types";
const body = (method: string, data: unknown) => ({ method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) });
const path = (step: string, project?: string): `/api/v1/${string}` => project ? `/api/v1/client-portal/projects/${project}/forms/${step}` : `/api/v1/form-responses/${step}`;
export const formsApi = {
  list: (search = "", page = 0) => apiRequest<FormTemplate[]>(`/api/v1/forms?search=${encodeURIComponent(search)}&page=${page}&size=20`),
  create: (name: string, description: string) => apiRequest<{ template: FormTemplate; definition: FormDefinition }>("/api/v1/forms", body("POST", { name, description })),
  template: (id: string) => apiRequest<FormTemplate>(`/api/v1/forms/${id}`),
  versions: (id: string, page = 0) => apiRequest<FormDefinition[]>(`/api/v1/forms/${id}/versions?page=${page}&size=20`),
  newVersion: (id: string, sourceVersionId?: string) => apiRequest<FormDefinition>(`/api/v1/forms/${id}/versions`, body("POST", { sourceVersionId })),
  save: (id: string, version: number, fields: FormField[]) => apiRequest<FormDefinition>(`/api/v1/form-versions/${id}/fields`, body("PUT", { version, fields })),
  publish: (id: string, version: number) => apiRequest<FormDefinition>(`/api/v1/form-versions/${id}/publish?version=${version}`, { method: "POST" }),
  archive: (id: string, version: number) => apiRequest(`/api/v1/forms/${id}/archive?version=${version}`, { method: "POST" }),
  view: (step: string, project?: string) => apiRequest<FormView>(path(step, project)),
  history: (step: string, project?: string, page = 0) => apiRequest<Submission[]>(`${path(step, project)}/submissions?page=${page}&size=10`),
  answer: (step: string, project: string, version: number, answers: Answers, submit: boolean) => apiRequest<FormView>(`${path(step, project)}/${submit ? "submit" : "draft"}`, body(submit ? "POST" : "PUT", { version, answers })),
  review: (step: string, version: number, decision: string, note: string) => apiRequest<FormView>(`${path(step)}/review`, body("POST", { version, decision, note })),
  exception: (step: string, version: number, action: "skip" | "reopen", note: string) => apiRequest<FormView>(`${path(step)}/${action}`, body("POST", { version, note })),
};
