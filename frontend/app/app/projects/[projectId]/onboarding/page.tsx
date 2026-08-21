"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, CheckCircle2, CircleDot, GitBranch, Loader2, LockKeyhole, ShieldCheck } from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { FormMessage } from "@/components/auth/form-message";
import { PageHeading } from "@/components/layout/page-heading";
import { PermissionState } from "@/components/shared/permission-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/services/api-client";
import type { Onboarding, OnboardingStep } from "@/types/onboarding";
import type { WorkflowStepType } from "@/types/workflow";

const MANUAL_STEP_TYPES = new Set<WorkflowStepType>(["APPROVAL", "WELCOME", "INSTRUCTION", "EXTERNAL_LINK", "VIDEO_GUIDE", "MEETING", "CUSTOM"]);

export default function ProjectOnboardingPage() {
  const params = useParams<{ projectId: string }>();
  const { authorizedRequest, hasPermission } = useAuth();
  const canRead = hasPermission("ONBOARDING_READ");
  const canReview = hasPermission("ONBOARDING_REVIEW");
  const [value, setValue] = useState<Onboarding | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyStep, setBusyStep] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setValue(await authorizedRequest<Onboarding>(`/projects/${params.projectId}/onboarding`));
    } catch (requestError) {
      setError(message(requestError, "Onboarding could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [authorizedRequest, params.projectId]);

  useEffect(() => { if (canRead) void load(); }, [canRead, load]);
  const blockers = useMemo(() => value?.steps.filter(step => step.blocking) ?? [], [value]);

  async function completeManual(stepId: string) {
    if (!value) return;
    setBusyStep(stepId);
    setError(null);
    try {
      await authorizedRequest(`/onboardings/${value.id}/steps/${stepId}/complete-manual`, { method: "POST" });
      await load();
    } catch (requestError) {
      setError(message(requestError, "The workflow requirement could not be completed."));
    } finally {
      setBusyStep(null);
    }
  }

  if (!canRead) return <PermissionState/>;
  if (loading) return <div className="panel flex min-h-72 items-center justify-center text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin"/>Loading onboarding snapshot…</div>;
  if (!value) return <div className="space-y-5"><Button asChild variant="ghost"><Link href={`/app/projects/${params.projectId}`}><ArrowLeft className="size-4"/>Back to project</Link></Button><FormMessage>{error ?? "Onboarding was not found."}</FormMessage></div>;

  return <div className="page-enter">
    <Button asChild variant="ghost" className="mb-5 -ml-2"><Link href={`/app/projects/${params.projectId}`}><ArrowLeft className="size-4"/>Back to project</Link></Button>
    <PageHeading eyebrow="Onboarding engine" title={value.templateName} description={`Immutable workflow snapshot · Version ${value.templateVersionNumber}. Readiness is derived from completed blocking requirements and final approval remains a separate server-side action.`} actions={(hasPermission("ONBOARDING_REVIEW") || hasPermission("ONBOARDING_APPROVE") || hasPermission("PROJECT_ACTIVATE")) ? <Button asChild><Link href={`/app/projects/${params.projectId}/review`}>Final review</Link></Button> : undefined}/>
    {error && <div className="mb-5"><FormMessage>{error}</FormMessage></div>}

    <div className="mb-6 grid gap-4 md:grid-cols-3"><Metric label="Lifecycle" value={value.status.replaceAll("_", " ")} icon={GitBranch}/><Metric label="Progress" value={`${value.progressPercent}%`} icon={CircleDot}/><Metric label="Blocking complete" value={`${blockers.filter(step => step.status === "COMPLETED").length}/${blockers.length}`} icon={ShieldCheck}/></div>

    <section className="panel overflow-hidden">
      <div className="flex flex-col gap-4 border-b bg-[hsl(var(--surface-subtle)/.5)] p-5 sm:flex-row sm:items-center sm:justify-between">
        <div><div className="flex flex-wrap items-center gap-2"><p className="font-semibold">Applicable workflow steps</p><StatusBadge status={value.status}/>{value.ready && <Badge variant="success"><CheckCircle2 className="mr-1 size-3"/>Ready foundation</Badge>}</div><p className="mt-1 text-xs leading-5 text-[hsl(var(--muted-foreground))]">Only steps whose validated conditions evaluated true were snapshotted. Dependencies refer to this snapshot, never the mutable template.</p></div>
        <div className="min-w-40"><div className="progress-track"><div className="progress-fill" style={{ width: `${Math.max(0, Math.min(100, value.progressPercent))}%` }}/></div></div>
      </div>
      <div className="divide-y">{value.steps.map(step => <StepRow key={step.id} step={step} canReview={canReview} busy={busyStep === step.id} onComplete={completeManual}/>)}</div>
    </section>
  </div>;
}

function StepRow({ step, canReview, busy, onComplete }: { step: OnboardingStep; canReview: boolean; busy: boolean; onComplete: (stepId: string) => Promise<void> }) {
  const manual = canReview && MANUAL_STEP_TYPES.has(step.stepType) && canCompleteManually(step);
  return <div className="grid gap-3 p-5 sm:grid-cols-[2.5rem_1fr_auto] sm:items-start">
    <div className={`grid size-9 place-items-center rounded-xl ${step.status === "COMPLETED" ? "bg-[hsl(var(--success-soft))] text-[hsl(var(--success))]" : step.status === "LOCKED" ? "bg-[hsl(var(--surface-subtle))] text-[hsl(var(--muted-foreground))]" : "bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]"}`}>{step.status === "COMPLETED" ? <CheckCircle2 className="size-4"/> : step.status === "LOCKED" ? <LockKeyhole className="size-4"/> : <CircleDot className="size-4"/>}</div>
    <div>
      <div className="flex flex-wrap items-center gap-2"><p className="font-semibold">{step.name}</p><Badge variant="neutral">{step.stepType}</Badge>{step.blocking && <Badge variant="danger">Blocking</Badge>}{!step.required && <Badge variant="neutral">Optional</Badge>}{step.clientVisible && <Badge variant="info">Client visible</Badge>}</div>
      <p className="mt-1 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{step.description || "No additional instruction."}</p>
      <p className="mt-2 text-xs text-[hsl(var(--muted-foreground))]">{step.dependencyMode === "NONE" ? "No prerequisites" : `${step.dependencyMode} dependency · ${step.dependencyKeys.join(", ") || "No applicable dependency"}`}{step.dueAt ? ` · Due ${formatDate(step.dueAt)}` : ""}</p>
      {manual && <Button size="sm" variant="secondary" className="mt-3" disabled={busy} onClick={() => void onComplete(step.id)}>{busy && <Loader2 className="size-4 animate-spin"/>}{manualLabel(step)}</Button>}
    </div>
    <StatusBadge status={step.status}/>
  </div>;
}

function canCompleteManually(step: OnboardingStep) {
  if (step.status === "UNDER_REVIEW") return true;
  if (step.clientVisible && step.requiresReview) return false;
  return step.status === "AVAILABLE" || step.status === "IN_PROGRESS" || step.status === "NEEDS_REVISION";
}
function manualLabel(step: OnboardingStep) {
  if (step.status === "UNDER_REVIEW") return "Approve requirement";
  if (step.stepType === "APPROVAL") return "Approve";
  if (step.requiresReview) return "Submit for review";
  return "Mark complete";
}
function Metric({ label, value, icon: Icon }: { label: string; value: string; icon: typeof GitBranch }) { return <div className="metric-card p-5"><div className="flex items-start justify-between"><div><p className="text-xs font-bold uppercase tracking-[.12em] text-[hsl(var(--muted-foreground))]">{label}</p><p className="mt-2 text-xl font-semibold tracking-[-.03em]">{value}</p></div><div className="grid size-9 place-items-center rounded-xl bg-[hsl(var(--surface-subtle))]"><Icon className="size-4"/></div></div></div>; }
function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
function message(error: unknown, fallback: string) { return error instanceof ApiClientError ? error.message : fallback; }
