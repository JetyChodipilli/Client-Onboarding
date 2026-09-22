"use client";

import {
  ArrowLeft,
  CheckCircle2,
  Circle,
  LockKeyhole,
  Play,
  RefreshCw,
  RotateCcw,
  Send,
  SkipForward,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { ApiClientError } from "@/lib/api-client";
import { Failure, PendingRows, errorMessage } from "./operations-pages";
import { operationsApi } from "./operations-api";
import { InvitationPanel } from "./invitation-panel";
import type {
  OnboardingStep,
  OnboardingView,
  Project,
  TemplateVersion,
  WorkflowTemplate,
} from "./types";

export function ProjectDetail({ projectId }: { projectId: string }) {
  const user = useCurrentUser();
  const [project, setProject] = useState<Project>();
  const [templates, setTemplates] = useState<WorkflowTemplate[]>([]);
  const [templateId, setTemplateId] = useState("");
  const [versions, setVersions] = useState<TemplateVersion[]>([]);
  const [versionId, setVersionId] = useState("");
  const [onboarding, setOnboarding] = useState<OnboardingView | null>();
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const canStart = user.permissions.includes("ONBOARDING_START");
  const canReview = user.permissions.includes("ONBOARDING_REVIEW");

  const load = useCallback(() => {
    Promise.all([
      operationsApi.project(projectId),
      operationsApi.templates(),
      operationsApi.onboardingForProject(projectId).catch((cause) => {
        if (cause instanceof ApiClientError && cause.status === 404) return null;
        throw cause;
      }),
    ])
      .then(([projectData, templateData, onboardingData]) => {
        setProject(projectData);
        const applicable = templateData.filter(
          (template) =>
            template.status === "ACTIVE" &&
            (!template.serviceId || template.serviceId === projectData.serviceId),
        );
        setTemplates(applicable);
        setTemplateId((value) => value || applicable[0]?.id || "");
        setOnboarding(onboardingData);
      })
      .catch((cause) => setError(errorMessage(cause)));
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!templateId) return;
    operationsApi
      .versions(templateId)
      .then((values) => {
        const published = values.filter((value) => value.status === "PUBLISHED");
        setVersions(published);
        setVersionId(published[0]?.id || "");
      })
      .catch((cause) => setError(errorMessage(cause)));
  }, [templateId]);

  async function start() {
    if (!project || !versionId) return;
    setPending(true);
    setError("");
    try {
      setOnboarding(
        await operationsApi.startOnboarding(
          project.id,
          versionId,
          project.version,
          crypto.randomUUID(),
        ),
      );
      setProject(await operationsApi.project(project.id));
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setPending(false);
    }
  }

  async function transition(step: OnboardingStep, targetStatus: OnboardingStep["status"]) {
    setPending(true);
    setError("");
    try {
      setOnboarding(await operationsApi.transitionStep(step.id, targetStatus, step.version));
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setPending(false);
    }
  }

  const blockers = useMemo(
    () =>
      onboarding?.steps.filter(
        (step) => step.applicable && step.blocking && step.status !== "COMPLETED",
      ) || [],
    [onboarding],
  );
  const yourAction = onboarding?.steps.find(
    (step) =>
      step.applicable &&
      ["AVAILABLE", "IN_PROGRESS", "NEEDS_REVISION"].includes(step.status),
  );
  const teamAction = onboarding?.steps.find(
    (step) => step.applicable && ["SUBMITTED", "UNDER_REVIEW"].includes(step.status),
  );
  const nextDeadline = onboarding?.steps
    .filter(
      (step) =>
        step.applicable && step.status !== "COMPLETED" && step.dueAt !== undefined,
    )
    .sort((left, right) =>
      String(left.dueAt).localeCompare(String(right.dueAt)),
    )[0];

  if (error && !project) {
    return (
      <>
        <Link
          href="/app/projects"
          className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-primary"
        >
          <ArrowLeft aria-hidden="true" className="size-4" />
          Projects
        </Link>
        <Failure message={error} retry={load} />
      </>
    );
  }
  if (!project || onboarding === undefined) {
    return <PendingRows label="Loading project workflow" />;
  }

  return (
    <>
      <Link
        href="/app/projects"
        className="inline-flex min-h-11 items-center gap-2 rounded-md text-sm font-semibold text-primary hover:underline"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Projects
      </Link>
      <div className="mt-4 flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-bold uppercase tracking-[0.15em] text-primary">
            Project workflow
          </p>
          <h1 className="mt-3 text-3xl font-bold tracking-[-0.035em] sm:text-4xl">
            {project.name}
          </h1>
          <p className="mt-3 text-muted-foreground">
            {project.clientName} · {project.serviceName}
          </p>
        </div>
        <Badge
          tone={
            project.status === "DRAFT"
              ? "info"
              : project.status === "COMPLETED"
                ? "success"
                : "neutral"
          }
        >
          {project.status}
        </Badge>
      </div>
      {error && (
        <Alert tone="error" className="mt-6">
          {error}
        </Alert>
      )}

      {!onboarding ? (
        <NotStarted
          project={project}
          templates={templates}
          versions={versions}
          templateId={templateId}
          versionId={versionId}
          canStart={canStart}
          pending={pending}
          setTemplateId={setTemplateId}
          setVersionId={setVersionId}
          start={start}
        />
      ) : (
        <>
          <div className="mt-8 grid gap-4 md:grid-cols-3">
            <Card>
              <p className="text-sm font-semibold text-muted-foreground">Current status</p>
              <p className="mt-2 text-2xl font-bold">{onboarding.onboarding.status}</p>
            </Card>
            <Card>
              <p className="text-sm font-semibold text-muted-foreground">Progress</p>
              <p className="mt-2 font-mono text-2xl font-bold">{onboarding.progress}%</p>
              <div
                className="mt-3 h-2 overflow-hidden rounded-full bg-muted"
                role="progressbar"
                aria-label="Onboarding progress"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={onboarding.progress}
              >
                <div
                  className="h-full rounded-full bg-primary"
                  style={{ width: `${onboarding.progress}%` }}
                />
              </div>
            </Card>
            <Card>
              <p className="text-sm font-semibold text-muted-foreground">Readiness</p>
              <p className="mt-2 text-2xl font-bold">
                {onboarding.onboarding.ready
                  ? "Ready for review"
                  : `${blockers.length} blocker${blockers.length === 1 ? "" : "s"}`}
              </p>
            </Card>
          </div>

          <section className="mt-6 grid gap-4 lg:grid-cols-2" aria-label="Onboarding action ownership">
            <Card className="border-primary/30">
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-primary">
                Your action
              </p>
              <h2 className="mt-3 text-lg font-bold">
                {yourAction?.name || "No action is currently available"}
              </h2>
              <StatusDetails
                nextAction={yourAction ? actionLabel(yourAction) : "Wait for a dependency or review"}
                blocker={blockers[0] ? `${blockers[0].name} · ${blockers[0].status}` : "None"}
                waitingFor={teamAction ? "Our onboarding team" : "You or the assigned internal owner"}
                deadline={nextDeadline?.dueAt}
                help="Review the step instructions or contact your onboarding administrator."
              />
            </Card>
            <Card>
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-muted-foreground">
                Waiting for our team
              </p>
              <h2 className="mt-3 text-lg font-bold">
                {teamAction?.name || "Nothing is waiting for internal review"}
              </h2>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                {teamAction
                  ? `${teamAction.status.replaceAll("_", " ")} · ${teamAction.assignedRole || "Onboarding team"}`
                  : "Submitted and under-review steps will appear here with their owner and deadline."}
              </p>
            </Card>
          </section>

          <section className="mt-8" aria-labelledby="workflow-steps">
            <div className="flex items-end justify-between gap-4">
              <div>
                <h2 id="workflow-steps" className="text-xl font-bold">
                  Workflow steps
                </h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Locked steps unlock only when their snapshot dependencies complete.
                </p>
              </div>
              <Badge>v{onboarding.onboarding.snapshotVersionNumber} snapshot</Badge>
            </div>
            <div className="mt-5 space-y-3">
              {onboarding.steps.map((step) => (
                <RuntimeStep
                  key={step.id}
                  step={step}
                  pending={pending}
                  canProgress={canStart}
                  canReview={canReview}
                  transition={transition}
                />
              ))}
            </div>
          </section>
          <InvitationPanel onboardingId={onboarding.onboarding.id} clientId={project.clientId} onboardingStatus={onboarding.onboarding.status} onChanged={load} />
        </>
      )}
    </>
  );
}

function NotStarted({
  project,
  templates,
  versions,
  templateId,
  versionId,
  canStart,
  pending,
  setTemplateId,
  setVersionId,
  start,
}: {
  project: Project;
  templates: WorkflowTemplate[];
  versions: TemplateVersion[];
  templateId: string;
  versionId: string;
  canStart: boolean;
  pending: boolean;
  setTemplateId: (value: string) => void;
  setVersionId: (value: string) => void;
  start: () => void;
}) {
  return (
    <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]">
      <Card>
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-primary">Your action</p>
        <h2 className="text-xl font-bold">Start onboarding</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Starting creates an immutable snapshot and moves this draft project to ONBOARDING.
        </p>
        {templates.length === 0 ? (
          <Alert className="mt-5">
            No active workflow template applies to service {project.serviceCode}. Publish one before
            starting onboarding.
          </Alert>
        ) : (
          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <label className="text-sm font-semibold">
              Template
              <select
                className="mt-2 h-12 w-full cursor-pointer rounded-md border bg-background px-3.5 text-base"
                value={templateId}
                onChange={(event) => setTemplateId(event.target.value)}
              >
                {templates.map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-sm font-semibold">
              Published version
              <select
                className="mt-2 h-12 w-full cursor-pointer rounded-md border bg-background px-3.5 text-base"
                value={versionId}
                onChange={(event) => setVersionId(event.target.value)}
              >
                {versions.map((version) => (
                  <option key={version.id} value={version.id}>
                    Version {version.versionNumber}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}
        {canStart && project.status === "DRAFT" && (
          <Button className="mt-5" onClick={start} disabled={pending || !versionId}>
            <Play aria-hidden="true" />
            {pending ? "Starting…" : "Start onboarding"}
          </Button>
        )}
      </Card>
      <Card>
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-muted-foreground">
          Waiting for our team
        </p>
        <h2 className="mt-3 font-bold">Current status</h2>
        <p className="mt-3 text-2xl font-bold">Not started</p>
        <p className="mt-2 text-sm font-semibold text-muted-foreground">Progress · 0%</p>
        <StatusDetails
          nextAction="Select a published version"
          blocker="Snapshot not created"
          waitingFor="Internal project owner"
          help="Publish an applicable workflow or contact your workflow administrator."
        />
      </Card>
    </div>
  );
}

function StatusDetails({
  nextAction,
  blocker,
  waitingFor,
  deadline,
  help,
}: {
  nextAction: string;
  blocker: string;
  waitingFor: string;
  deadline?: string;
  help: string;
}) {
  const values = [
    ["Next action", nextAction],
    ["Blocking reason", blocker],
    ["Waiting for", waitingFor],
    [
      "Required deadline",
      deadline
        ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(
            new Date(deadline),
          )
        : "No deadline set",
    ],
    ["Available help", help],
  ];
  return (
    <dl className="mt-5 space-y-3 text-sm">
      {values.map(([label, value]) => (
        <div key={label} className="flex items-start justify-between gap-4">
          <dt className="shrink-0 text-muted-foreground">{label}</dt>
          <dd className="text-right font-semibold [overflow-wrap:anywhere]">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function actionLabel(step: OnboardingStep) {
  if (step.status === "AVAILABLE") return "Start this step";
  if (step.status === "NEEDS_REVISION") return "Revise and resubmit";
  if (step.requiresReview) return "Submit for internal review";
  return "Complete this step";
}

function RuntimeStep({
  step,
  pending,
  canProgress,
  canReview,
  transition,
}: {
  step: OnboardingStep;
  pending: boolean;
  canProgress: boolean;
  canReview: boolean;
  transition: (step: OnboardingStep, target: OnboardingStep["status"]) => void;
}) {
  const Icon =
    step.status === "COMPLETED"
      ? CheckCircle2
      : step.status === "LOCKED"
        ? LockKeyhole
        : Circle;
  const canSkip =
    canProgress && step.allowSkip && ["AVAILABLE", "IN_PROGRESS"].includes(step.status);

  return (
    <article className={`rounded-xl border bg-card p-5 ${!step.applicable ? "opacity-60" : ""}`}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <Icon
            aria-hidden="true"
            className={`mt-0.5 size-5 shrink-0 ${
              step.status === "COMPLETED" ? "text-success" : "text-muted-foreground"
            }`}
          />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="[overflow-wrap:anywhere] font-bold">{step.name}</h3>
              {step.blocking && <Badge tone="warning">Blocking</Badge>}
              {!step.applicable && <Badge>Not applicable</Badge>}
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              {step.status}
              {step.assignedRole ? ` · ${step.assignedRole}` : ""}
              {step.dueAt
                ? ` · Due ${new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(
                    new Date(step.dueAt),
                  )}`
                : ""}
            </p>
          </div>
        </div>
        {step.applicable && (
          <div className="flex flex-wrap gap-2">
            {step.status === "AVAILABLE" && canProgress && (
              <Button
                size="sm"
                variant="outline"
                disabled={pending}
                onClick={() => transition(step, "IN_PROGRESS")}
              >
                <Play aria-hidden="true" />Start
              </Button>
            )}
            {step.status === "NEEDS_REVISION" && canProgress && (
              <Button
                size="sm"
                variant="outline"
                disabled={pending}
                onClick={() => transition(step, "IN_PROGRESS")}
              >
                <RotateCcw aria-hidden="true" />Revise
              </Button>
            )}
            {step.status === "IN_PROGRESS" && step.requiresReview && canProgress && (
              <Button size="sm" disabled={pending} onClick={() => transition(step, "SUBMITTED")}>
                <Send aria-hidden="true" />Submit
              </Button>
            )}
            {step.status === "IN_PROGRESS" && !step.requiresReview && canProgress && (
              <Button size="sm" disabled={pending} onClick={() => transition(step, "COMPLETED")}>
                <CheckCircle2 aria-hidden="true" />Complete
              </Button>
            )}
            {step.status === "SUBMITTED" && canReview && (
              <Button
                size="sm"
                disabled={pending}
                onClick={() => transition(step, "UNDER_REVIEW")}
              >
                <RefreshCw aria-hidden="true" />Review
              </Button>
            )}
            {step.status === "UNDER_REVIEW" && canReview && (
              <>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={pending}
                  onClick={() => transition(step, "NEEDS_REVISION")}
                >
                  <RotateCcw aria-hidden="true" />Request revision
                </Button>
                <Button size="sm" disabled={pending} onClick={() => transition(step, "COMPLETED")}>
                  <CheckCircle2 aria-hidden="true" />Approve
                </Button>
              </>
            )}
            {canSkip && (
              <Button
                size="sm"
                variant="ghost"
                disabled={pending}
                onClick={() => transition(step, "SKIPPED")}
              >
                <SkipForward aria-hidden="true" />Skip
              </Button>
            )}
            {step.status === "COMPLETED" && step.allowReopen && canProgress && (
              <Button
                size="sm"
                variant="outline"
                disabled={pending}
                onClick={() => transition(step, "IN_PROGRESS")}
              >
                <RotateCcw aria-hidden="true" />Reopen
              </Button>
            )}
          </div>
        )}
      </div>
    </article>
  );
}
