"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, CheckCircle2, Clock3, LockKeyhole, Loader2, UsersRound } from "lucide-react";
import { useClientAuth } from "@/auth/client-auth-provider";
import { StatusBadge } from "@/components/shared/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/services/api-client";
import type { ClientPortalNextAction, ClientPortalProjectDetail, ClientPortalStep } from "@/types/client-portal";
import type { WorkflowStepType } from "@/types/workflow";

const SIMPLE_CLIENT_STEPS = new Set<WorkflowStepType>([
  "WELCOME",
  "INSTRUCTION",
  "EXTERNAL_LINK",
  "VIDEO_GUIDE",
  "MEETING",
  "CUSTOM",
]);

export default function PortalProject() {
  const { projectId } = useParams<{ projectId: string }>();
  const { authorizedRequest } = useClientAuth();
  const [data, setData] = useState<ClientPortalProjectDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyStep, setBusyStep] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await authorizedRequest<ClientPortalProjectDetail>(`/client-portal/projects/${projectId}`));
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "Project could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [authorizedRequest, projectId]);

  useEffect(() => { void load(); }, [load]);

  async function completeSimple(stepId: string) {
    setBusyStep(stepId);
    setError(null);
    try {
      await authorizedRequest(`/client-portal/projects/${projectId}/steps/${stepId}/complete`, { method: "POST" });
      await load();
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "The requirement could not be completed.");
    } finally {
      setBusyStep(null);
    }
  }

  if (loading) {
    return <div className="grid min-h-80 place-items-center text-sm text-[hsl(var(--muted-foreground))]"><span className="flex items-center gap-2"><Loader2 className="size-4 animate-spin"/>Loading project…</span></div>;
  }
  if (!data) {
    return <div className="panel p-8"><p className="font-semibold">Project unavailable</p><p className="mt-2 text-sm text-[hsl(var(--muted-foreground))]">{error}</p><Button asChild variant="secondary" className="mt-5"><Link href="/portal"><ArrowLeft className="size-4"/>Back to workspace</Link></Button></div>;
  }

  return <div className="page-enter">
    <Button asChild variant="ghost" size="sm" className="mb-5 -ml-2"><Link href="/portal"><ArrowLeft className="size-4"/>All projects</Link></Button>
    <header className="mb-7 grid gap-5 lg:grid-cols-[1fr_auto] lg:items-end">
      <div>
        <div className="flex flex-wrap items-center gap-2"><StatusBadge status={data.onboardingStatus ?? data.projectStatus}/><Badge variant={data.waitingFor === "YOU" ? "warning" : "info"}>{data.waitingFor === "YOU" ? "Your action" : "Waiting for our team"}</Badge></div>
        <h1 className="mt-3 text-3xl font-semibold tracking-[-.045em] sm:text-4xl">{data.projectName}</h1>
        <p className="mt-2 text-sm text-[hsl(var(--muted-foreground))]">{data.serviceName} · {data.clientName}</p>
      </div>
      <div className="metric-card min-w-52 p-4">
        <div className="flex items-baseline justify-between"><span className="text-xs font-semibold uppercase tracking-[.12em] text-[hsl(var(--muted-foreground))]">Progress</span><span className="text-2xl font-semibold">{data.progressPercent}%</span></div>
        <div className="progress-track mt-3"><div className="progress-fill" style={{ width: `${Math.max(0, Math.min(100, data.progressPercent))}%` }}/></div>
        <p className="mt-2 text-xs text-[hsl(var(--muted-foreground))]">{data.completedRequirements} of {data.totalRequirements} requirements complete</p>
      </div>
    </header>

    {error && <div role="alert" className="mb-5 rounded-xl border border-[hsl(var(--danger)/.22)] bg-[hsl(var(--danger-soft))] px-4 py-3 text-sm text-[hsl(var(--danger))]">{error}</div>}

    <section className="next-action-card mb-7 p-5 sm:p-6">
      <p className="text-xs font-bold uppercase tracking-[.14em] text-[hsl(var(--primary))]">Current status</p>
      <p className="mt-2 text-lg font-semibold">{data.blockingReason}</p>
      <p className="mt-2 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{data.helpText}</p>
      {data.nextAction && <div className="mt-5 flex flex-col gap-3 rounded-2xl bg-[hsl(var(--primary-soft))] p-4 sm:flex-row sm:items-center sm:justify-between">
        <div><p className="text-xs font-bold uppercase tracking-[.12em] text-[hsl(var(--primary))]">Your action</p><p className="mt-1 font-semibold">{data.nextAction.title}</p><p className="mt-1 text-sm text-[hsl(var(--muted-foreground))]">{data.nextAction.description}</p></div>
        <NextActionButton action={data.nextAction} projectId={data.projectId} requiresReview={data.yourAction.find(step => step.id === data.nextAction?.stepId)?.requiresReview ?? false} busy={busyStep === data.nextAction.stepId} onSimpleComplete={completeSimple}/>
      </div>}
    </section>

    <div className="grid gap-5 xl:grid-cols-2">
      <StepSection projectId={data.projectId} title="Your action" icon={Clock3} items={data.yourAction} empty="Nothing needs your input right now." busyStep={busyStep} onSimpleComplete={completeSimple}/>
      <StepSection projectId={data.projectId} title="Waiting for our team" icon={UsersRound} items={data.waitingForOurTeam} empty="No submissions are waiting on our team." busyStep={busyStep} onSimpleComplete={completeSimple}/>
      <StepSection projectId={data.projectId} title="Locked" icon={LockKeyhole} items={data.locked} empty="No client-visible requirements are locked." busyStep={busyStep} onSimpleComplete={completeSimple}/>
      <StepSection projectId={data.projectId} title="Completed" icon={CheckCircle2} items={data.completed} empty="Completed requirements will appear here." busyStep={busyStep} onSimpleComplete={completeSimple}/>
    </div>
  </div>;
}

function NextActionButton({ action, projectId, requiresReview, busy, onSimpleComplete }: { action: ClientPortalNextAction; projectId: string; requiresReview: boolean; busy: boolean; onSimpleComplete: (stepId: string) => Promise<void> }) {
  const type = action.actionType as WorkflowStepType;
  if (type === "FORM") return <Button asChild><Link href={`/portal/projects/${projectId}/forms/${action.stepId}`}>Open questionnaire</Link></Button>;
  if (type === "FILE_UPLOAD") return <Button asChild><Link href={`/portal/projects/${projectId}/assets/${action.stepId}`}>Upload assets</Link></Button>;
  if (type === "PLATFORM_ACCESS") return <Button asChild><Link href={`/portal/projects/${projectId}/access/${action.stepId}`}>Grant platform access</Link></Button>;
  if (type === "CONTRACT") return <Button asChild><Link href={`/portal/projects/${projectId}/contracts`}>Review contract</Link></Button>;
  if (type === "PAYMENT") return <Button asChild><Link href={`/portal/projects/${projectId}/invoices`}>Review invoice</Link></Button>;
  if (SIMPLE_CLIENT_STEPS.has(type)) return <Button disabled={busy} onClick={() => void onSimpleComplete(action.stepId)}>{busy && <Loader2 className="size-4 animate-spin"/>}{simpleActionLabel(type, requiresReview)}</Button>;
  return null;
}

function StepSection({ projectId, title, icon: Icon, items, empty, busyStep, onSimpleComplete }: { projectId: string; title: string; icon: typeof Clock3; items: ClientPortalStep[]; empty: string; busyStep: string | null; onSimpleComplete: (stepId: string) => Promise<void> }) {
  return <section className="panel overflow-hidden">
    <div className="flex items-center gap-2 border-b px-5 py-4"><Icon className="size-4"/><h2 className="font-semibold">{title}</h2><Badge variant="neutral">{items.length}</Badge></div>
    {items.length === 0 ? <p className="p-5 text-sm text-[hsl(var(--muted-foreground))]">{empty}</p> : <div className="divide-y">{items.map(step => <div key={step.id} className="p-5">
      <div className="flex flex-wrap items-center gap-2"><p className="font-medium">{step.name}</p><StatusBadge status={step.status}/>{step.required && <Badge variant="neutral">Required</Badge>}</div>
      {step.description && <p className="mt-2 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{step.description}</p>}
      {step.lockedReason && <p className="mt-3 rounded-xl bg-[hsl(var(--surface-subtle))] px-3 py-2 text-xs text-[hsl(var(--muted-foreground))]">{step.lockedReason}</p>}
      {step.dueAt && <p className="mt-3 text-xs text-[hsl(var(--muted-foreground))]">Due {new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(new Date(step.dueAt))}</p>}
      <StepAction step={step} projectId={projectId} busy={busyStep === step.id} onSimpleComplete={onSimpleComplete}/>
    </div>)}</div>}
  </section>;
}

function StepAction({ step, projectId, busy, onSimpleComplete }: { step: ClientPortalStep; projectId: string; busy: boolean; onSimpleComplete: (stepId: string) => Promise<void> }) {
  if (step.status === "LOCKED" || step.status === "COMPLETED" || step.status === "SKIPPED") return null;
  if (step.stepType === "FORM") return <Button asChild variant="secondary" size="sm" className="mt-3"><Link href={`/portal/projects/${projectId}/forms/${step.id}`}>Open questionnaire</Link></Button>;
  if (step.stepType === "FILE_UPLOAD") return <Button asChild variant="secondary" size="sm" className="mt-3"><Link href={`/portal/projects/${projectId}/assets/${step.id}`}>Open secure upload</Link></Button>;
  if (step.stepType === "PLATFORM_ACCESS") return <Button asChild variant="secondary" size="sm" className="mt-3"><Link href={`/portal/projects/${projectId}/access/${step.id}`}>Open access guide</Link></Button>;
  if (step.stepType === "CONTRACT") return <Button asChild variant="secondary" size="sm" className="mt-3"><Link href={`/portal/projects/${projectId}/contracts`}>Open contracts</Link></Button>;
  if (step.stepType === "PAYMENT") return <Button asChild variant="secondary" size="sm" className="mt-3"><Link href={`/portal/projects/${projectId}/invoices`}>Open invoices</Link></Button>;
  if (SIMPLE_CLIENT_STEPS.has(step.stepType)) return <Button variant="secondary" size="sm" className="mt-3" disabled={busy} onClick={() => void onSimpleComplete(step.id)}>{busy && <Loader2 className="size-4 animate-spin"/>}{simpleActionLabel(step.stepType, step.requiresReview)}</Button>;
  return null;
}

function simpleActionLabel(type: WorkflowStepType, requiresReview: boolean) {
  if (requiresReview) return "Submit for review";
  if (type === "WELCOME" || type === "INSTRUCTION") return "Mark as read";
  if (type === "MEETING") return "Confirm meeting complete";
  return "Mark complete";
}
