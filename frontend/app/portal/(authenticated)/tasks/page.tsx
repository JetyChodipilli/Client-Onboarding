"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ArrowRight, CheckCircle2, ClipboardCheck, Loader2 } from "lucide-react";
import { useClientAuth } from "@/auth/client-auth-provider";
import { EmptyState } from "@/components/shared/empty-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/services/api-client";
import type { TaskItem, TaskStatus } from "@/types/tasks";

export default function ClientTasksPage() {
  const { authorizedRequest } = useClientAuth();
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTasks(await authorizedRequest<TaskItem[]>("/client-portal/tasks?size=100"));
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Tasks could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [authorizedRequest]);

  useEffect(() => { void load(); }, [load]);

  async function transition(task: TaskItem, status: TaskStatus) {
    setBusyId(task.id);
    setError(null);
    try {
      await authorizedRequest(`/client-portal/tasks/${task.id}/transition`, {
        method: "POST",
        body: JSON.stringify({ status, version: task.version }),
      });
      await load();
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "The task could not be updated. Please try again.");
    } finally {
      setBusyId(null);
    }
  }

  return <div className="page-enter">
    <header className="mb-8"><p className="text-xs font-bold uppercase tracking-[.16em] text-[hsl(var(--primary))]">Your tasks</p><h1 className="mt-2 text-3xl font-semibold tracking-[-.045em]">Assigned work, without the noise</h1><p className="mt-3 max-w-2xl text-sm leading-6 text-[hsl(var(--muted-foreground))]">Only work explicitly assigned to your client identity and authorized projects appears here.</p></header>
    {error && <div className="mb-5 rounded-2xl border border-[hsl(var(--danger)/.25)] bg-[hsl(var(--danger)/.05)] p-4 text-sm text-[hsl(var(--danger))]" role="alert">{error}</div>}
    {loading ? <Loading/> : tasks.length === 0 ? <EmptyState title="Nothing assigned to you" description="When your project team assigns a task to you, it will appear here."/> : <div className="grid gap-4 lg:grid-cols-2">{tasks.map((task) => <TaskCard key={task.id} task={task} busy={busyId === task.id} onTransition={transition}/>)}</div>}
  </div>;
}

function TaskCard({ task, busy, onTransition }: { task: TaskItem; busy: boolean; onTransition: (task: TaskItem, status: TaskStatus) => Promise<void> }) {
  return <article className="panel interactive-card p-5 sm:p-6">
    <div className="flex items-start justify-between gap-3"><div className="grid size-10 place-items-center rounded-xl bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]"><ClipboardCheck className="size-4.5"/></div><StatusBadge status={task.status}/></div>
    <h2 className="mt-4 font-semibold">{task.title}</h2>
    {task.description && <p className="mt-2 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{task.description}</p>}
    <div className="mt-3 flex flex-wrap gap-2"><Badge variant="neutral">{task.priority}</Badge>{task.dueAt && <Badge variant="warning">Due {date(task.dueAt)}</Badge>}</div>
    <div className="mt-5 flex flex-wrap items-center gap-2">
      {task.projectId && <Button asChild size="sm" variant="secondary"><Link href={`/portal/projects/${task.projectId}`}>Project<ArrowRight className="size-4"/></Link></Button>}
      {task.status === "TODO" && <Button size="sm" disabled={busy} onClick={() => void onTransition(task, "IN_PROGRESS")}>{busy && <Loader2 className="size-4 animate-spin"/>}Start</Button>}
      {task.status === "IN_PROGRESS" && <Button size="sm" disabled={busy} onClick={() => void onTransition(task, "IN_REVIEW")}>{busy && <Loader2 className="size-4 animate-spin"/>}Submit</Button>}
      {task.status === "IN_REVIEW" && <span className="inline-flex items-center gap-2 text-xs font-medium text-[hsl(var(--muted-foreground))]"><CheckCircle2 className="size-4"/>Waiting for your team</span>}
      {task.status === "COMPLETED" && <span className="inline-flex items-center gap-2 text-xs font-medium text-[hsl(var(--success))]"><CheckCircle2 className="size-4"/>Completed</span>}
    </div>
  </article>;
}

function Loading() { return <div className="flex min-h-72 items-center justify-center text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin"/>Loading tasks…</div>; }
function date(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(new Date(value)); }
