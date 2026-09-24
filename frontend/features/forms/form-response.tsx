"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { portalApi } from "@/features/portal/portal-api";
import type { PortalDashboard } from "@/features/portal/types";
import { ApiClientError } from "@/lib/api-client";
import { formsApi } from "./forms-api";
import { formControl, QuestionFields } from "./question-fields";
import type { Answer, Answers, FormView, Submission } from "./types";

export function InternalFormResponse({ stepId }: { stepId: string }) {
  const user = useCurrentUser();
  if (!user.permissions.some((p) => ["FORM_READ", "FORM_REVIEW"].includes(p))) return <Alert>You do not have permission to read form responses.</Alert>;
  return <FormResponsePanel stepId={stepId} canReview={user.permissions.includes("FORM_REVIEW")} />;
}

export function FormResponsePanel({ stepId, projectId, canReview = false }: { stepId: string; projectId?: string; canReview?: boolean }) {
  const [view, setView] = useState<FormView>(); const [dashboard, setDashboard] = useState<PortalDashboard>();
  const [answers, setAnswers] = useState<Answers>({}); const [history, setHistory] = useState<Submission[]>([]);
  const [page, setPage] = useState(0); const [dirty, setDirty] = useState(false); const [pending, setPending] = useState(false);
  const [error, setError] = useState(""); const [message, setMessage] = useState(""); const [note, setNote] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({}); const errorRef = useRef<HTMLDivElement>(null);
  const load = useCallback(async () => {
    const [v, h, d] = await Promise.all([formsApi.view(stepId, projectId), formsApi.history(stepId, projectId), projectId ? portalApi.dashboard(projectId) : Promise.resolve(undefined)]);
    setView(v); setAnswers(v.response.answers); setHistory(h); setDashboard(d); setPage(0); setDirty(false);
  }, [stepId, projectId]);
  useEffect(() => { let active = true; formsApi.view(stepId, projectId).then(async (v) => {
    const [h, d] = await Promise.all([formsApi.history(stepId, projectId), projectId ? portalApi.dashboard(projectId) : Promise.resolve(undefined)]);
    if (active) { setView(v); setAnswers(v.response.answers); setHistory(h); setDashboard(d); }
  }).catch((e) => { if (active) setError(e instanceof Error ? e.message : "The form could not be loaded."); }); return () => { active = false; }; }, [stepId, projectId]);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  useEffect(() => { const leave = (e: BeforeUnloadEvent) => { if (dirty) e.preventDefault(); }; window.addEventListener("beforeunload", leave); return () => window.removeEventListener("beforeunload", leave); }, [dirty]);
  const editable = Boolean(projectId && view && ["DRAFT", "NEEDS_REVISION"].includes(view.response.status) && ["AVAILABLE", "IN_PROGRESS", "NEEDS_REVISION"].includes(view.stepStatus) && view.projectStatus === "ONBOARDING" && view.onboardingStatus === "IN_PROGRESS");
  const reviewable = Boolean(!projectId && canReview && view && ["SUBMITTED", "UNDER_REVIEW"].includes(view.response.status) && view.projectStatus === "ONBOARDING" && view.onboardingStatus === "IN_PROGRESS");
  const exceptionAction = !projectId && canReview && view?.projectStatus === "ONBOARDING" && view.onboardingStatus === "IN_PROGRESS" ? view.allowReopen && view.stepStatus === "COMPLETED" ? "reopen" : view.allowSkip && ["AVAILABLE", "IN_PROGRESS"].includes(view.stepStatus) ? "skip" : undefined : undefined;
  async function applyException() {
    if (!view || !exceptionAction) return; setPending(true); setError("");
    try { setView(await formsApi.exception(stepId, view.response.version, exceptionAction, note)); setNote(""); setHistory(await formsApi.history(stepId)); setPage(0); setMessage(exceptionAction === "reopen" ? "Reopened. The previous submissions remain in history." : "Skipped according to this workflow's rules."); }
    catch (e) { failure(e); } finally { setPending(false); }
  }
  function failure(cause: unknown) {
    setError(cause instanceof Error ? cause.message : "The form could not be updated. Try again.");
    if (cause instanceof ApiClientError) setFieldErrors(Object.fromEntries(cause.fieldErrors.map((e) => [e.field, e.message])));
  }
  async function save(submit: boolean) {
    if (!view || !projectId) return;
    setPending(true); setError(""); setMessage(""); setFieldErrors({});
    try { const next = await formsApi.answer(stepId, projectId, view.response.version, answers, submit); setView(next); setAnswers(next.response.answers); setDirty(false); setMessage(submit ? "Your answers have been submitted." : "Draft saved. You can return to it later."); setHistory(await formsApi.history(stepId, projectId)); setPage(0); setDashboard(await portalApi.dashboard(projectId)); }
    catch (e) { failure(e); } finally { setPending(false); }
  }
  async function review(decision: string) {
    if (!view) return; setPending(true); setError(""); setMessage("");
    try { setView(await formsApi.review(stepId, view.response.version, decision, note)); setNote(""); setHistory(await formsApi.history(stepId)); setPage(0); setMessage(decision === "NEEDS_REVISION" ? "Revision requested. The client can update their answers." : decision === "APPROVED" ? "Approved. The workflow step is complete." : "Review started."); }
    catch (e) { failure(e); } finally { setPending(false); }
  }
  async function reload() { if (dirty && !window.confirm("Discard unsaved answers and load the saved form?")) return; setPending(true); setError(""); try { await load(); setFieldErrors({}); } catch (e) { failure(e); } finally { setPending(false); } }
  async function historyPage(next: number) { setPending(true); try { setHistory(await formsApi.history(stepId, projectId, next)); setPage(next); } catch (e) { failure(e); } finally { setPending(false); } }
  function update(key: string, value: Answer | undefined) { setAnswers((current) => { const next = { ...current }; if (value === undefined) delete next[key]; else next[key] = value; return next; }); setDirty(true); setMessage(""); setFieldErrors((current) => { const next = { ...current }; delete next[key]; return next; }); }
  return <>
    <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" href={projectId ? `/portal/projects/${projectId}` : view ? `/app/projects/${view.projectId}` : "/app/projects"} onClick={(e) => { if (dirty && !window.confirm("Leave this page without saving your answers?")) e.preventDefault(); }}>← Back to project</Link>
    {error && <div ref={errorRef} tabIndex={-1} className="mt-5"><Alert tone="error"><p>{error}</p>{Object.entries(fieldErrors).map(([key, value]) => <a key={key} href={`#answer-${key}`} className="mt-2 block underline">{view?.definition.fields.find((f) => f.key === key)?.label ?? key}: {value}</a>)}<Button variant="outline" className="mt-3" onClick={reload} disabled={pending}>Reload saved response</Button></Alert></div>}
    {!view ? !error && <p className="mt-6" aria-busy="true">Loading questionnaire…</p> : <>
      <div className="mt-5 flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm font-semibold text-primary">Questionnaire · version {view.definition.versionNumber}</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">{view.formName}</h1><p className="mt-3 text-muted-foreground">{view.stepName}</p></div><Badge aria-label="Form response status" tone={view.response.status === "APPROVED" ? "success" : "info"}>{view.response.status.replaceAll("_", " ")}</Badge></div>
      <section className="mt-6 grid gap-4 md:grid-cols-2" aria-label="Form status and next action"><Card><h2 className="text-sm font-bold">Your action</h2><p className="mt-2 text-sm text-muted-foreground">{editable ? "Complete the questions below. Save a draft any time, then submit when ready." : reviewable ? "Review the submitted answers, then approve or explain the revisions needed." : "No action is available right now."}</p><p className="mt-3 text-sm"><strong>Deadline:</strong> {view.deadline ? new Date(view.deadline).toLocaleDateString() : "No deadline set"}</p>{dashboard && <p className="mt-2 text-sm"><strong>Project progress:</strong> {dashboard.progress}%</p>}</Card><Card><h2 className="text-sm font-bold">Waiting for our team</h2><p className="mt-2 text-sm text-muted-foreground">{["SUBMITTED", "UNDER_REVIEW"].includes(view.response.status) ? "Your submitted answers are with the project team for review." : "No review is pending."}</p><p className="mt-3 text-sm"><strong>Blocking reason:</strong> {dashboard?.blockingReason || (view.projectStatus !== "ONBOARDING" || view.onboardingStatus !== "IN_PROGRESS" ? "Project or onboarding updates are paused or closed." : view.stepStatus === "LOCKED" ? "Complete the prerequisite shown on the project page." : "None")}</p><p className="mt-2 text-sm"><strong>Help:</strong> {dashboard?.availableHelp || "Contact the project owner for help."}</p></Card></section>
      {view.response.reviewNote && <Alert className="mt-5"><strong>Reviewer feedback</strong><p className="mt-1 whitespace-pre-wrap">{view.response.reviewNote}</p></Alert>}
      {message && <Alert tone="success" className="mt-5">{message}</Alert>}
      <form noValidate onSubmit={(e) => { e.preventDefault(); void save(true); }} className="mt-6 max-w-3xl"><Card><QuestionFields fields={view.definition.fields} answers={answers} update={update} disabled={!editable || pending} errors={fieldErrors} />
        {editable && <div className="mt-8 border-t pt-5"><p className="mb-4 text-sm text-muted-foreground" role="status">{dirty ? "You have unsaved answers." : view.response.updatedAt ? `Saved ${new Date(view.response.updatedAt).toLocaleString()}` : "Your draft has not been saved yet."}</p><div className="flex flex-wrap gap-3"><Button type="submit" disabled={pending}>{pending ? "Working…" : view.response.submissionNumber > 0 ? "Resubmit answers" : "Submit answers"}</Button><Button variant="outline" onClick={() => save(false)} disabled={pending}>Save draft</Button></div></div>}
      </Card></form>
      {reviewable && <Card className="mt-6 max-w-3xl"><h2 className="text-xl font-bold">Review this submission</h2><label htmlFor="review-note" className="mt-4 block text-sm font-semibold">Feedback (required for revision)</label><textarea id="review-note" className={`${formControl} min-h-24`} maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} disabled={pending} /><div className="mt-4 flex flex-wrap gap-3"><Button onClick={() => review("APPROVED")} disabled={pending}>Approve answers</Button><Button variant="outline" onClick={() => review("NEEDS_REVISION")} disabled={pending || !note.trim()}>Request revision</Button>{view.response.status === "SUBMITTED" && <Button variant="ghost" onClick={() => review("UNDER_REVIEW")} disabled={pending}>Start review</Button>}</div></Card>}
      {exceptionAction && <Card className="mt-6 max-w-3xl"><h2 className="text-lg font-bold">{exceptionAction === "reopen" ? "Reopen this questionnaire" : "Skip this questionnaire"}</h2><label htmlFor="exception-note" className="mt-4 block text-sm font-semibold">Reason (required)</label><textarea id="exception-note" className={formControl} maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} /><Button variant="outline" className="mt-4" disabled={pending || !note.trim()} onClick={applyException}>{exceptionAction === "reopen" ? "Confirm reopen" : "Confirm skip"}</Button></Card>}
      <section className="mt-10 max-w-3xl" aria-labelledby="history-heading"><h2 id="history-heading" className="text-xl font-bold">Submission history</h2><p className="mt-2 text-sm text-muted-foreground">Each submission keeps its original answers and review decisions.</p>{history.length === 0 ? <p className="mt-4 text-sm">No answers have been submitted yet.</p> : <div className="mt-4 space-y-3">{history.map((submission) => <details key={submission.id} className="rounded-xl border bg-card p-4"><summary className="min-h-11 cursor-pointer font-semibold">Submission {submission.submissionNumber} · {new Date(submission.createdAt).toLocaleDateString()}</summary><dl className="mt-4 space-y-3">{Object.entries(submission.answers).map(([key, value]) => <div key={key}><dt className="text-sm font-semibold">{view.definition.fields.find((f) => f.key === key)?.label || key}</dt><dd className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground [overflow-wrap:anywhere]">{Array.isArray(value) ? value.join(", ") : String(value)}</dd></div>)}</dl>{submission.reviews.map((review, index) => <p key={index} className="mt-4 border-t pt-3 text-sm"><strong>{review.decision.replaceAll("_", " ")}</strong>{review.note && ` — ${review.note}`}</p>)}</details>)}</div>}<div className="mt-4 flex gap-3"><Button variant="outline" disabled={pending || page === 0} onClick={() => historyPage(page - 1)}>Previous submissions</Button><Button variant="outline" disabled={pending || history.length < 10} onClick={() => historyPage(page + 1)}>Older submissions</Button></div></section>
    </>}
  </>;
}
