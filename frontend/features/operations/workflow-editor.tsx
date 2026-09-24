"use client";

import { FormVersionPicker } from "@/features/forms/form-version-picker";

import { ArrowDown, ArrowLeft, ArrowUp, GitBranch, Plus, Save, Send, Trash2 } from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { cn } from "@/lib/utils";
import { Failure, PendingRows, errorMessage } from "./operations-pages";
import { operationsApi } from "./operations-api";
import type { TemplateVersion, VersionBundle, WorkflowCondition, WorkflowStep, WorkflowTemplate } from "./types";

const selectClass = "h-11 w-full cursor-pointer rounded-md border bg-background px-3 text-sm text-foreground disabled:cursor-not-allowed disabled:opacity-60";
const stepTypes: WorkflowStep["stepType"][] = ["WELCOME", "INSTRUCTION", "FORM", "FILE_UPLOAD", "PAYMENT", "CONTRACT", "PLATFORM_ACCESS", "MANUAL_TASK", "APPROVAL", "EXTERNAL_LINK", "VIDEO_GUIDE", "MEETING", "CUSTOM"];
const conditionFields: WorkflowCondition["field"][] = ["SERVICE_CODE", "PROJECT_VALUE_MINOR", "CLIENT_STATUS", "CURRENCY_CODE"];

export function WorkflowEditor({ templateId }: { templateId: string }) {
  const user = useCurrentUser();
  const [template, setTemplate] = useState<WorkflowTemplate>();
  const [versions, setVersions] = useState<TemplateVersion[]>();
  const [bundle, setBundle] = useState<VersionBundle>();
  const [steps, setSteps] = useState<WorkflowStep[]>([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [pending, setPending] = useState(false);
  const [dirty, setDirty] = useState(false);
  const canManage = user.permissions.includes("WORKFLOW_MANAGE");
  const editable = canManage && bundle?.version.status === "DRAFT";

  const load = () => {
    setError("");
    Promise.all([operationsApi.template(templateId), operationsApi.versions(templateId)])
      .then(async ([templateData, versionData]) => {
        setTemplate(templateData); setVersions(versionData);
        if (versionData[0]) await selectVersion(versionData[0].id);
      }).catch((cause) => setError(errorMessage(cause)));
  };
  useEffect(() => {
    Promise.all([operationsApi.template(templateId), operationsApi.versions(templateId)])
      .then(async ([templateData, versionData]) => {
        setTemplate(templateData); setVersions(versionData);
        if (versionData[0]) {
          const data = await operationsApi.version(versionData[0].id);
          setBundle(data); setSteps(data.steps);
        }
      }).catch((cause) => setError(errorMessage(cause)));
  }, [templateId]);

  async function selectVersion(versionId: string) {
    setPending(true); setError(""); setSuccess("");
    try { const data = await operationsApi.version(versionId); setBundle(data); setSteps(data.steps); setDirty(false); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setPending(false); }
  }

  function update(id: string, changes: Partial<WorkflowStep>) {
    setSteps((current) => current.map((step) => step.id === id ? { ...step, ...changes } : step)); setDirty(true);
  }

  function addStep() {
    const id = crypto.randomUUID();
    setSteps((current) => [...current, { id, stepKey: `STEP_${current.length + 1}`, name: "New step", stepType: "MANUAL_TASK", displayOrder: current.length, required: true, blocking: true, clientVisible: true, requiresReview: false, dependencyMode: "NONE", allowSkip: false, allowReopen: false, configuration: {}, dependencyStepIds: [], version: 0 }]);
    setDirty(true);
  }

  function move(index: number, direction: -1 | 1) {
    const target = index + direction; if (target < 0 || target >= steps.length) return;
    setSteps((current) => { const copy = [...current]; [copy[index], copy[target]] = [copy[target], copy[index]]; return copy.map((step, order) => ({ ...step, displayOrder: order })); }); setDirty(true);
  }

  function remove(id: string) {
    setSteps((current) => current.filter((step) => step.id !== id).map((step, order) => ({ ...step, displayOrder: order, dependencyStepIds: step.dependencyStepIds.filter((dependency) => dependency !== id), dependencyMode: step.dependencyStepIds.filter((dependency) => dependency !== id).length ? step.dependencyMode : "NONE" }))); setDirty(true);
  }

  async function save() {
    if (!bundle) return; setPending(true); setError(""); setSuccess("");
    try { const saved = await operationsApi.replaceSteps(bundle.version.id, bundle.version.version, steps.map((step, order) => ({ ...step, displayOrder: order }))); setBundle(saved); setSteps(saved.steps); setDirty(false); setSuccess("Draft saved and graph validation passed."); await refreshVersions(); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setPending(false); }
  }

  async function publish() {
    if (!bundle) return; setPending(true); setError(""); setSuccess("");
    try { const published = await operationsApi.publish(bundle.version.id, bundle.version.version); setBundle(published); setSteps(published.steps); setSuccess("Version published. It is now immutable and available for new onboarding instances."); await refreshVersions(); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setPending(false); }
  }

  async function newDraft() {
    const published = versions?.find((version) => version.status === "PUBLISHED");
    setPending(true); setError("");
    try { const created = await operationsApi.createVersion(templateId, published?.id); await refreshVersions(); await selectVersion(created.version.id); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setPending(false); }
  }

  async function refreshVersions() { setVersions(await operationsApi.versions(templateId)); }
  const blockerCount = useMemo(() => steps.filter((step) => step.blocking).length, [steps]);

  if (error && !template) return <><Link href="/app/workflows" className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-primary"><ArrowLeft aria-hidden="true" className="size-4" />Workflows</Link><Failure message={error} retry={load} /></>;
  if (!template || !versions) return <PendingRows label="Loading workflow builder" />;

  return <><Link href="/app/workflows" className="inline-flex min-h-11 items-center gap-2 rounded-md text-sm font-semibold text-primary hover:underline"><ArrowLeft aria-hidden="true" className="size-4" />Workflow templates</Link>
    <div className="mt-4 flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between"><div><div className="flex flex-wrap items-center gap-2"><p className="text-sm font-bold uppercase tracking-[0.15em] text-primary">Workflow builder</p><Badge tone={template.status === "ACTIVE" ? "success" : "neutral"}>{template.status}</Badge></div><h1 className="mt-3 max-w-3xl text-3xl font-bold tracking-[-0.035em] sm:text-4xl">{template.name}</h1><p className="mt-3 max-w-2xl text-muted-foreground">{template.description || "Configure dependencies, conditions, blockers, assignments, due dates, visibility, review, skip, and reopen rules."}</p></div>{canManage && <Button variant="outline" onClick={newDraft} disabled={pending || versions.some((version) => version.status === "DRAFT")}><Plus aria-hidden="true" />New draft</Button>}</div>
    {error && <Alert tone="error" className="mt-6">{error}</Alert>}{success && <Alert tone="success" className="mt-6">{success}</Alert>}
    <div className="mt-8 grid gap-6 xl:grid-cols-12"><section className="min-w-0 xl:col-span-8" aria-labelledby="steps-title"><div className="flex flex-col gap-4 rounded-xl border bg-card p-5 sm:flex-row sm:items-end sm:justify-between"><div><label htmlFor="workflow-version" className="block text-sm font-semibold">Template version</label><select id="workflow-version" className={cn(selectClass, "mt-2 min-w-52")} value={bundle?.version.id || ""} onChange={(event) => selectVersion(event.target.value)} disabled={pending}>{versions.map((version) => <option key={version.id} value={version.id}>v{version.versionNumber} · {version.status}</option>)}</select></div>{editable && <Button variant="outline" onClick={addStep}><Plus aria-hidden="true" />Add step</Button>}</div>
      <div className="mt-5 flex items-center justify-between gap-4"><div><h2 id="steps-title" className="text-xl font-bold">Ordered steps</h2><p className="mt-1 text-sm text-muted-foreground">Dependencies reference stable step IDs; reordering does not rewrite them.</p></div><Badge>{steps.length} steps</Badge></div>
      {steps.length === 0 ? <Card className="mt-5"><h3 className="font-bold">This draft has no steps</h3><p className="mt-2 text-sm text-muted-foreground">Add at least one step before publishing.</p></Card> : <div className="mt-5 space-y-4">{steps.map((step, index) => <StepEditor key={step.id} step={step} allSteps={steps} index={index} editable={Boolean(editable)} update={update} move={move} remove={remove} />)}</div>}
    </section>
      <aside className="xl:col-span-4"><div className="sticky top-6 space-y-4"><Card><div className="flex items-center gap-3"><GitBranch aria-hidden="true" className="size-5 text-primary" /><h2 className="font-bold">Version proof</h2></div><dl className="mt-5 space-y-4 text-sm"><div className="flex justify-between gap-4 border-b pb-3"><dt className="text-muted-foreground">Status</dt><dd className="font-semibold">{bundle?.version.status || "—"}</dd></div><div className="flex justify-between gap-4 border-b pb-3"><dt className="text-muted-foreground">Blocking steps</dt><dd className="font-mono font-semibold">{blockerCount}</dd></div><div className="flex justify-between gap-4 border-b pb-3"><dt className="text-muted-foreground">Conditional steps</dt><dd className="font-mono font-semibold">{steps.filter((step) => step.condition).length}</dd></div><div className="flex justify-between gap-4"><dt className="text-muted-foreground">Unsaved changes</dt><dd className="font-semibold">{dirty ? "Yes" : "No"}</dd></div></dl></Card>
        {bundle?.version.status === "PUBLISHED" ? <Alert>This version is immutable. Create a new draft to make changes; existing onboarding snapshots remain unchanged.</Alert> : editable ? <Card><h2 className="font-bold">Draft actions</h2><p className="mt-2 text-sm text-muted-foreground">Save validates the graph. Publish locks the version for future onboarding snapshots.</p><div className="mt-5 grid gap-3"><Button variant="outline" onClick={save} disabled={pending || !dirty || !steps.length}><Save aria-hidden="true" />{pending ? "Working…" : "Save draft"}</Button><Button onClick={publish} disabled={pending || dirty || !steps.length}><Send aria-hidden="true" />Publish version</Button></div></Card> : <Alert>You have read-only access to workflow versions.</Alert>}
      </div></aside></div>
  </>;
}

function StepEditor({ step, allSteps, index, editable, update, move, remove }: {
  step: WorkflowStep;
  allSteps: WorkflowStep[];
  index: number;
  editable: boolean;
  update: (id: string, changes: Partial<WorkflowStep>) => void;
  move: (index: number, direction: -1 | 1) => void;
  remove: (id: string) => void;
}) {
  const candidates = allSteps.filter((candidate) => candidate.id !== step.id);
  const toggleDependency = (id: string, checked: boolean) => {
    const dependencies = checked ? [...step.dependencyStepIds, id] : step.dependencyStepIds.filter((value) => value !== id);
    update(step.id, { dependencyStepIds: dependencies, dependencyMode: dependencies.length ? step.dependencyMode === "NONE" ? "ALL" : step.dependencyMode : "NONE" });
  };
  return <article className="rounded-xl border bg-card p-5" aria-labelledby={`step-${step.id}`}><div className="flex flex-wrap items-start justify-between gap-4"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className="grid size-7 place-items-center rounded-full bg-secondary font-mono text-xs font-bold">{index + 1}</span><Badge>{step.stepType}</Badge>{step.blocking && <Badge tone="warning">Blocking</Badge>}{step.condition && <Badge tone="info">Conditional</Badge>}</div><h3 id={`step-${step.id}`} className="mt-3 [overflow-wrap:anywhere] text-lg font-bold">{step.name}</h3><p className="mt-1 font-mono text-xs text-muted-foreground">{step.stepKey}</p></div>{editable && <div className="flex gap-1"><Button size="icon" variant="ghost" aria-label={`Move ${step.name} up`} onClick={() => move(index, -1)} disabled={index === 0}><ArrowUp aria-hidden="true" /></Button><Button size="icon" variant="ghost" aria-label={`Move ${step.name} down`} onClick={() => move(index, 1)} disabled={index === allSteps.length - 1}><ArrowDown aria-hidden="true" /></Button><Button size="icon" variant="ghost" aria-label={`Remove ${step.name}`} onClick={() => remove(step.id)}><Trash2 aria-hidden="true" /></Button></div>}</div>
    <fieldset disabled={!editable} className="mt-5 grid gap-4 border-t pt-5 sm:grid-cols-2"><legend className="sr-only">Configure {step.name}</legend><label className="text-sm font-semibold">Name<Input className="mt-2" value={step.name} maxLength={180} onChange={(event) => update(step.id, { name: event.target.value })} /></label><label className="text-sm font-semibold">Stable key<Input className="mt-2 font-mono" value={step.stepKey} maxLength={80} onChange={(event) => update(step.id, { stepKey: event.target.value.toUpperCase() })} /></label><label className="text-sm font-semibold">Step type<select className={cn(selectClass, "mt-2")} value={step.stepType} onChange={(event) => update(step.id, { stepType: event.target.value as WorkflowStep["stepType"] })}>{stepTypes.map((type) => <option key={type}>{type}</option>)}</select></label><label className="text-sm font-semibold">Assigned role<Input className="mt-2" value={step.assignedRole || ""} maxLength={100} onChange={(event) => update(step.id, { assignedRole: event.target.value || undefined })} /></label><label className="text-sm font-semibold">Due after hours<Input className="mt-2" type="number" min={0} max={87600} value={step.dueAfterHours ?? ""} onChange={(event) => update(step.id, { dueAfterHours: event.target.value ? Number(event.target.value) : undefined })} /></label><label className="text-sm font-semibold">Dependency rule<select className={cn(selectClass, "mt-2")} value={step.dependencyMode} onChange={(event) => update(step.id, { dependencyMode: event.target.value as WorkflowStep["dependencyMode"], dependencyStepIds: event.target.value === "NONE" ? [] : step.dependencyStepIds })}><option value="NONE">None · immediately available</option><option value="ALL">All dependencies complete</option><option value="ANY">Any dependency complete</option></select></label></fieldset>
    {step.stepType === "FORM" && <FormVersionPicker value={typeof step.configuration?.formVersionId === "string" ? step.configuration.formVersionId : undefined} disabled={!editable} onChange={(id) => update(step.id, { configuration: { ...step.configuration, formVersionId: id } })} />}
    <fieldset disabled={!editable || step.dependencyMode === "NONE"} className="mt-5"><legend className="text-sm font-semibold">Dependencies</legend><div className="mt-2 grid gap-2 sm:grid-cols-2">{candidates.length ? candidates.map((candidate) => <label key={candidate.id} className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-sm"><input type="checkbox" className="size-4 accent-primary" checked={step.dependencyStepIds.includes(candidate.id)} onChange={(event) => toggleDependency(candidate.id, event.target.checked)} /><span className="[overflow-wrap:anywhere]">{candidate.name}</span></label>) : <p className="text-sm text-muted-foreground">Add another step to create a dependency.</p>}</div></fieldset>
    <fieldset disabled={!editable} className="mt-5"><legend className="text-sm font-semibold">Rules</legend><div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{([ ["required", "Required"], ["blocking", "Blocking"], ["clientVisible", "Client visible"], ["requiresReview", "Requires review"], ["allowSkip", "Can skip"], ["allowReopen", "Can reopen"] ] as const).map(([field, label]) => { const disabled = field === "allowSkip" && step.blocking; return <label key={field} className={`flex min-h-11 items-center gap-3 rounded-md border px-3 text-sm ${disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}><input type="checkbox" className="size-4 accent-primary" checked={step[field]} disabled={disabled} onChange={(event) => update(step.id, field === "blocking" && event.target.checked ? { blocking: true, allowSkip: false } : { [field]: event.target.checked })} />{label}{disabled && <span className="sr-only">Unavailable for blocking steps</span>}</label>; })}</div></fieldset>
    <fieldset disabled={!editable} className="mt-5"><legend className="text-sm font-semibold">Condition</legend><label className="mt-2 flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-sm"><input type="checkbox" className="size-4 accent-primary" checked={Boolean(step.condition)} onChange={(event) => update(step.id, { condition: event.target.checked ? { field: "SERVICE_CODE", operator: "EQUALS", value: "" } : undefined })} />Apply this step only when a validated condition matches</label>{step.condition && <div className="mt-3 grid gap-3 sm:grid-cols-3"><select aria-label="Condition field" className={selectClass} value={step.condition.field} onChange={(event) => update(step.id, { condition: { ...step.condition!, field: event.target.value as WorkflowCondition["field"] } })}>{conditionFields.map((field) => <option key={field}>{field}</option>)}</select><select aria-label="Condition operator" className={selectClass} value={step.condition.operator} onChange={(event) => update(step.id, { condition: { ...step.condition!, operator: event.target.value as WorkflowCondition["operator"] } })}><option>EQUALS</option><option>NOT_EQUALS</option><option>GREATER_THAN</option><option>GREATER_THAN_OR_EQUAL</option><option>IN</option></select><Input aria-label="Condition value" value={step.condition.value} maxLength={300} onChange={(event) => update(step.id, { condition: { ...step.condition!, value: event.target.value } })} /></div>}</fieldset>
  </article>;
}
