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
import { assetsApi, uploadFile } from "./assets-api";
import { control, fileSize, mimeLabels, validateFile, type AssetFile, type AssetVersion, type AssetView } from "./types";

export function InternalAssetResponse({ stepId }: { stepId: string }) {
  const user = useCurrentUser();
  if (!user.permissions.some((p) => ["ASSET_READ", "ASSET_REVIEW"].includes(p))) return <Alert>You do not have permission to read project files.</Alert>;
  return <AssetResponsePanel stepId={stepId} canReview={user.permissions.includes("ASSET_REVIEW")} />;
}

export function AssetResponsePanel({ stepId, projectId, canReview = false }: { stepId: string; projectId?: string; canReview?: boolean }) {
  const [view, setView] = useState<AssetView>(); const [dashboard, setDashboard] = useState<PortalDashboard>();
  const [history, setHistory] = useState<AssetVersion[]>([]); const [page, setPage] = useState(0); const [file, setFile] = useState<File>();
  const [error, setError] = useState(""); const [message, setMessage] = useState(""); const [note, setNote] = useState("");
  const [pending, setPending] = useState(false); const [stage, setStage] = useState(""); const [progress, setProgress] = useState(0);
  const errorRef = useRef<HTMLDivElement>(null); const inputRef = useRef<HTMLInputElement>(null); const transfer = useRef<AbortController | undefined>(undefined);
  const load = useCallback(async () => {
    const [v, h, d] = await Promise.all([assetsApi.view(stepId, projectId), assetsApi.history(stepId, projectId), projectId ? portalApi.dashboard(projectId) : Promise.resolve(undefined)]);
    return { v, h, d };
  }, [stepId, projectId]);
  const receive = useCallback((value: Awaited<ReturnType<typeof load>>) => { setView(value.v); setHistory(value.h); setDashboard(value.d); setPage(0); }, []);
  useEffect(() => { let active = true; load().then((value) => { if (active) receive(value); }).catch((e) => { if (active) setError(e instanceof Error ? e.message : "Files could not be loaded."); }); return () => { active = false; transfer.current?.abort(); }; }, [load, receive]);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  useEffect(() => { const leave = (e: BeforeUnloadEvent) => { if (file || pending) e.preventDefault(); }; window.addEventListener("beforeunload", leave); return () => window.removeEventListener("beforeunload", leave); }, [file, pending]);
  const active = view?.projectStatus === "ONBOARDING" && view.onboardingStatus === "IN_PROGRESS";
  const editable = Boolean(projectId && active && view && ["AVAILABLE", "IN_PROGRESS", "NEEDS_REVISION"].includes(view.stepStatus));
  const replaceable = editable && (!view?.current || ["REQUESTED", "UPLOADED", "NEEDS_REVISION", "QUARANTINED", "REJECTED"].includes(view.current.status));
  const retryable = editable && view?.current && ["REQUESTED", "UPLOADED", "SCANNING"].includes(view.current.status);
  const reviewable = !projectId && canReview && active && view?.current && ["SUBMITTED", "UNDER_REVIEW"].includes(view.current.status) && view.current.scanStatus === "CLEAN";
  const exception = !projectId && canReview && active && view ? view.allowReopen && view.stepStatus === "COMPLETED" ? "reopen" : view.allowSkip && ["AVAILABLE", "IN_PROGRESS"].includes(view.stepStatus) && view.current?.status !== "SCANNING" ? "skip" : undefined : undefined;
  function failure(e: unknown) { setError(e instanceof Error ? e.message : "The file action failed. Please reload and try again."); }
  async function reload() { setPending(true); setError(""); try { receive(await load()); } catch (e) { failure(e); } finally { setPending(false); } }
  async function scan(version: number) {
    if (!projectId) return; setStage("Checking file safety…");
    const next = await assetsApi.submit(stepId, projectId, version); setView(next);
    receive(await load());
    setMessage(next.current?.scanStatus === "CLEAN" ? next.current.status === "APPROVED" ? "File accepted. This step is complete." : "File submitted. Your project team will review it." : "File checks need your attention. Read the status below before continuing.");
  }
  async function upload(event: React.FormEvent) {
    event.preventDefault(); if (!file || !view || !projectId) return;
    const invalid = validateFile(file, view.requirement); if (invalid) { setError(invalid); return; }
    setPending(true); setError(""); setMessage(""); setProgress(0); setStage("Preparing file…");
    const abort = new AbortController(); transfer.current = abort;
    try {
      const hash = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
      const sha256 = Array.from(new Uint8Array(hash)).map((b) => b.toString(16).padStart(2, "0")).join("");
      if (abort.signal.aborted) return;
      const target = await assetsApi.upload(stepId, projectId, view.version, file, sha256); setView(target.asset); setStage("Uploading file…");
      await uploadFile(target.upload, file, setProgress, abort.signal);
      setFile(undefined); if (inputRef.current) inputRef.current.value = "";
      await scan(target.asset.version);
    } catch (e) { failure(e); try { receive(await load()); } catch (refreshError) { setError(`${e instanceof Error ? e.message : "Upload failed."} Status refresh also failed: ${refreshError instanceof Error ? refreshError.message : "Use Reload file status to try again."}`); } }
    finally { setPending(false); setStage(""); transfer.current = undefined; }
  }
  async function retry() { if (!view) return; setPending(true); setError(""); setMessage(""); try { await scan(view.version); } catch (e) { failure(e); } finally { setPending(false); setStage(""); } }
  async function review(decision: string) { if (!view) return; setPending(true); setError(""); setMessage(""); try { setView(await assetsApi.review(stepId, view.version, decision, note)); setNote(""); receive(await load()); setMessage(decision === "APPROVED" ? "File approved. The workflow step is complete." : decision === "NEEDS_REVISION" ? "Revision requested. The client can upload a new version." : "Review started."); } catch (e) { failure(e); } finally { setPending(false); } }
  async function applyException() { if (!view || !exception) return; setPending(true); setError(""); try { setView(await assetsApi.exception(stepId, view.version, exception, note)); setNote(""); receive(await load()); setMessage(exception === "reopen" ? "Reopened. The client can provide a replacement." : "Skipped according to the workflow rules."); } catch (e) { failure(e); } finally { setPending(false); } }
  async function download(value: AssetFile) { setPending(true); setError(""); try { const signed = await assetsApi.download(stepId, value.id, projectId); const link = document.createElement("a"); link.href = signed.url; link.target = "_blank"; link.rel = "noopener noreferrer"; link.referrerPolicy = "no-referrer"; link.click(); } catch (e) { failure(e); } finally { setPending(false); } }
  async function historyPage(next: number) { setPending(true); setError(""); try { setHistory(await assetsApi.history(stepId, projectId, next)); setPage(next); } catch (e) { failure(e); } finally { setPending(false); } }
  return <>
    <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:underline" href={projectId ? `/portal/projects/${projectId}` : view ? `/app/projects/${view.projectId}` : "/app/projects"} onClick={(e) => { if ((file || pending) && !window.confirm("Leave this page? Any unfinished upload will need to be started again.")) e.preventDefault(); }}>← Back to project</Link>
    {error && <div ref={errorRef} tabIndex={-1} className="mt-5"><Alert tone="error"><p>{error}</p><Button variant="outline" className="mt-3" onClick={reload} disabled={pending}>Reload file status</Button></Alert></div>}
    {!view ? !error && <p className="mt-6" aria-busy="true">Loading project files…</p> : <>
      <div className="mt-5 flex flex-wrap items-start justify-between gap-4"><div className="min-w-0 flex-1"><h1 className="max-w-5xl text-3xl font-bold tracking-tight [overflow-wrap:anywhere] sm:text-4xl">{view.requirement.name}</h1><p className="mt-3 text-muted-foreground">{view.stepName}</p></div><Badge aria-label="Asset status" tone={view.current?.status === "APPROVED" ? "success" : ["REJECTED", "QUARANTINED"].includes(view.current?.status ?? "") ? "warning" : "info"}>{(view.current?.status ?? "REQUESTED").replaceAll("_", " ")}</Badge></div>
      <section className="mt-6 grid grid-flow-dense gap-4 md:grid-cols-2" aria-label="File status and next action"><Card><h2 className="text-sm font-bold">Your action</h2><p className="mt-2 text-sm text-muted-foreground">{replaceable ? "Choose the requested file, then upload and submit it. Replacements keep previous versions in history." : reviewable ? "Download the checked file, then approve it or explain what needs to change." : retryable ? "Refresh the status or retry file checks if a previous attempt was interrupted." : "No file action is available right now."}</p><p className="mt-3 text-sm"><strong>Deadline:</strong> {view.deadline ? new Date(view.deadline).toLocaleDateString() : "No deadline set"}</p>{dashboard && <p className="mt-2 text-sm"><strong>Project progress:</strong> {dashboard.progress}%</p>}</Card><Card><h2 className="text-sm font-bold">Waiting for our team</h2><p className="mt-2 text-sm text-muted-foreground">{["SUBMITTED", "UNDER_REVIEW"].includes(view.current?.status ?? "") ? "Your file is with the project team for review." : view.current?.status === "SCANNING" ? "Your file is being checked for safety." : "No review is pending."}</p><p className="mt-3 text-sm"><strong>Blocking reason:</strong> {!active ? "Project or onboarding updates are paused or closed." : view.current?.scanStatus === "ERROR" ? "File checks are unavailable. Retry or contact your project team." : view.current?.status === "QUARANTINED" ? "This file was quarantined. A safe replacement is needed." : dashboard?.blockingReason || (view.stepStatus === "LOCKED" ? "Complete the prerequisite shown on the project page." : "None")}</p><p className="mt-2 text-sm"><strong>Help:</strong> {dashboard?.availableHelp || "Contact the project owner for help."}</p></Card></section>
      {message && <Alert className="mt-5" tone={view.current && ["ERROR", "INFECTED"].includes(view.current.scanStatus) || view.current?.status === "REJECTED" ? "error" : "success"}>{message}</Alert>}
      <div className="mt-6 grid grid-flow-dense gap-6 lg:grid-cols-12"><Card className="min-w-0 lg:col-span-7"><h2 className="text-xl font-bold">{view.current ? "Current file" : "Provide your file"}</h2><p className="mt-3 whitespace-pre-wrap text-sm text-muted-foreground [overflow-wrap:anywhere]">{view.requirement.instructions}</p><p id="file-types" className="mt-3 text-sm">Accepted: {view.requirement.allowedMimes.map((m) => mimeLabels[m]).join(", ")}. Maximum {fileSize(view.requirement.maxBytes)}.</p>
        {view.current && <div className="mt-5 rounded-lg border bg-muted/40 p-4"><p className="font-semibold [overflow-wrap:anywhere]">{view.current.filename}</p><p className="mt-1 text-sm text-muted-foreground">Version {view.current.versionNumber} · {fileSize(view.current.byteSize)}</p><p className="mt-3 text-sm" role="status">{view.current.scanMessage || "File checks have not completed yet."}</p>{view.current.reviewNote && <div className="mt-4 border-t pt-3"><h3 className="text-sm font-bold">Reviewer feedback</h3><p className="mt-1 whitespace-pre-wrap text-sm [overflow-wrap:anywhere]">{view.current.reviewNote}</p></div>}{view.current.downloadable && <Button variant="outline" className="mt-4" disabled={pending} onClick={() => download(view.current!)}>Download current file</Button>}</div>}
        {replaceable && <form className="mt-6" onSubmit={upload}><label htmlFor="asset-file" className="block text-sm font-semibold">Choose a file</label><input ref={inputRef} id="asset-file" type="file" accept={view.requirement.allowedMimes.join(",")} aria-describedby="file-types" disabled={pending} className={`${control} cursor-pointer py-3 text-sm file:mr-3 file:cursor-pointer file:rounded file:border-0 file:bg-muted file:px-3 file:py-2 file:font-semibold file:text-foreground`} onChange={(e) => { const chosen = e.target.files?.[0]; setError(""); setMessage(""); if (chosen) { const invalid = validateFile(chosen, view.requirement); if (invalid) { setError(invalid); setFile(undefined); e.target.value = ""; return; } } setFile(chosen); }} /><p className="mt-3 text-sm text-muted-foreground">Downloads become available only after validation and malware scanning pass.</p><Button type="submit" className="mt-5" disabled={pending || !file}>{pending ? "Working…" : "Upload and submit file"}</Button></form>}
        {retryable && !pending && <Button variant="outline" className="mt-4" onClick={retry}>Retry file checks</Button>}
        {stage && <div className="mt-5" aria-live="polite"><p className="text-sm font-semibold">{stage}</p>{stage === "Uploading file…" && <><progress aria-label="File upload progress" className="mt-3 h-3 w-full accent-primary" value={progress} max={100} /><p className="mt-1 text-sm">{progress}% uploaded</p><Button variant="ghost" className="mt-2" onClick={() => transfer.current?.abort()}>Cancel upload</Button></>}{stage === "Checking file safety…" && <p className="mt-2 text-sm text-muted-foreground">This can take a minute. Keep this page open, or reload the file status when you return.</p>}</div>}
      </Card><div className="min-w-0 space-y-5 lg:col-span-5">{reviewable ? <Card><h2 className="text-xl font-bold">Review this file</h2><label htmlFor="asset-review-note" className="mt-4 block text-sm font-semibold">Feedback (required for revision)</label><textarea id="asset-review-note" className={`${control} min-h-24`} value={note} maxLength={2000} disabled={pending} onChange={(e) => setNote(e.target.value)} /><div className="mt-4 flex flex-wrap gap-3"><Button disabled={pending} onClick={() => review("APPROVED")}>Approve file</Button><Button variant="outline" disabled={pending || !note.trim()} onClick={() => review("NEEDS_REVISION")}>Request revision</Button>{view.current?.status === "SUBMITTED" && <Button variant="ghost" disabled={pending} onClick={() => review("UNDER_REVIEW")}>Start review</Button>}</div></Card> : <Card><h2 className="font-bold">Version history stays intact</h2><p className="mt-3 text-sm leading-6 text-muted-foreground">Each replacement becomes a new version. Your team can compare checked files and see previous review decisions below.</p></Card>}
        {exception && <Card><h2 className="font-bold">{exception === "reopen" ? "Reopen this requirement" : "Skip this requirement"}</h2><label htmlFor="asset-exception" className="mt-4 block text-sm font-semibold">Reason (required)</label><textarea id="asset-exception" className={control} value={note} maxLength={2000} onChange={(e) => setNote(e.target.value)} /><Button variant="outline" className="mt-4" disabled={pending || !note.trim()} onClick={applyException}>{exception === "reopen" ? "Confirm reopen" : "Confirm skip"}</Button></Card>}</div></div>
      <section className="mt-10" aria-labelledby="asset-history"><h2 id="asset-history" className="text-xl font-bold">File version history</h2>{!history.length ? <p className="mt-4 text-sm text-muted-foreground">No file versions yet.</p> : <ul className="mt-4 space-y-3">{history.map(({ file: entry, reviews }) => <li key={entry.id}><details className="rounded-xl border bg-card p-4"><summary className="min-h-11 cursor-pointer font-semibold [overflow-wrap:anywhere]">Version {entry.versionNumber} · {entry.filename}</summary><div className="mt-3"><Badge>{entry.status.replaceAll("_", " ")}</Badge><p className="mt-3 text-sm">{new Date(entry.createdAt).toLocaleString()} · {fileSize(entry.byteSize)}</p><p className="mt-2 text-sm text-muted-foreground">{entry.scanMessage || "File checks have not completed."}</p>{entry.downloadable && <Button variant="outline" className="mt-3" disabled={pending} onClick={() => download(entry)}>Download version {entry.versionNumber}</Button>}{reviews.map((r, i) => <p key={i} className="mt-4 border-t pt-3 text-sm [overflow-wrap:anywhere]"><strong>{r.decision.replaceAll("_", " ")}</strong>{r.note && ` — ${r.note}`}</p>)}</div></details></li>)}</ul>}<div className="mt-4 flex flex-wrap gap-3"><Button variant="outline" disabled={pending || page === 0} onClick={() => historyPage(page - 1)}>Newer versions</Button><Button variant="outline" disabled={pending || history.length < 10} onClick={() => historyPage(page + 1)}>Older versions</Button><Button variant="ghost" disabled={pending} onClick={reload}>Refresh status</Button></div></section>
    </>}
  </>;
}
