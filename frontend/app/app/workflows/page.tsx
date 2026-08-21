"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowRight, GitBranch, Loader2, Plus, RefreshCw, Search, Sparkles } from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { FormMessage } from "@/components/auth/form-message";
import { PageHeading } from "@/components/layout/page-heading";
import { EmptyState } from "@/components/shared/empty-state";
import { PermissionState } from "@/components/shared/permission-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/services/api-client";
import type { WorkflowTemplate, WorkflowTemplateSummary } from "@/types/workflow";

export default function WorkflowsPage() {
  const { authorizedRequest, hasPermission } = useAuth();
  const canRead = hasPermission("WORKFLOW_READ") || hasPermission("WORKFLOW_MANAGE");
  const canManage = hasPermission("WORKFLOW_MANAGE");
  const [items, setItems] = useState<WorkflowTemplateSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setItems(await authorizedRequest<WorkflowTemplateSummary[]>("/workflow-templates?size=100")); }
    catch (e) { setError(message(e, "Workflow templates could not be loaded.")); }
    finally { setLoading(false); }
  }, [authorizedRequest]);
  useEffect(() => { if (canRead) void load(); }, [canRead, load]);

  const filtered = useMemo(() => {
    const value = query.trim().toLowerCase();
    return value ? items.filter(item => `${item.name} ${item.description ?? ""}`.toLowerCase().includes(value)) : items;
  }, [items, query]);

  if (!canRead) return <PermissionState />;
  return <>
    <PageHeading eyebrow="Workflow engine" title="Onboarding workflows" description="Design reusable onboarding blueprints, publish immutable versions, and keep every active onboarding pinned to the exact version it started with." actions={<div className="flex gap-2"><Button variant="secondary" size="icon" onClick={() => void load()} aria-label="Refresh workflows"><RefreshCw className="size-4" /></Button>{canManage && <Button onClick={() => setShowCreate(v => !v)}><Plus className="size-4" />New workflow</Button>}</div>} />

    <div className="mb-6 grid gap-4 lg:grid-cols-3">
      <Metric icon={GitBranch} label="Templates" value={items.length} help="Reusable tenant-owned workflows" />
      <Metric icon={Sparkles} label="Published" value={items.filter(i => i.latestPublishedVersion).length} help="Safe to start new onboarding" />
      <Metric icon={Plus} label="Drafts" value={items.filter(i => i.draftVersion).length} help="Changes not affecting live instances" />
    </div>

    {showCreate && canManage && <CreateWorkflow onCancel={() => setShowCreate(false)} onCreated={(template) => { setShowCreate(false); setItems(current => [{ id: template.id, name: template.name, description: template.description, status: template.status, latestPublishedVersion: null, latestPublishedVersionId: null, draftVersion: 1, updatedAt: template.updatedAt, version: template.version }, ...current]); }} />}
    {error && <div className="mb-5"><FormMessage>{error}</FormMessage></div>}

    <section className="panel overflow-hidden">
      <div className="border-b p-4 sm:p-5"><label className="relative block max-w-xl"><span className="sr-only">Search workflows</span><Search className="pointer-events-none absolute left-3.5 top-3.5 size-4 text-[hsl(var(--muted-foreground))]" /><Input className="pl-10" value={query} onChange={e => setQuery(e.target.value)} placeholder="Search workflow templates…" /></label></div>
      {loading ? <Loading /> : filtered.length === 0 ? <EmptyState title={query ? "No matching workflows" : "No workflows yet"} description={query ? "Try a broader search." : "Create a reusable workflow, configure its steps, then publish an immutable version."} /> : <div className="divide-y">{filtered.map(item => <Link key={item.id} href={`/app/workflows/${item.id}`} className="group grid gap-4 p-5 transition-colors hover:bg-[hsl(var(--surface-subtle)/.55)] sm:grid-cols-[1fr_auto] sm:items-center sm:p-6"><div className="flex min-w-0 gap-4"><div className="grid size-11 shrink-0 place-items-center rounded-2xl bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]"><GitBranch className="size-5" /></div><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h2 className="truncate font-semibold tracking-[-.015em]">{item.name}</h2><StatusBadge status={item.status} />{item.latestPublishedVersion && <Badge variant="success">Published v{item.latestPublishedVersion}</Badge>}{item.draftVersion && <Badge variant="warning">Draft v{item.draftVersion}</Badge>}</div><p className="mt-1.5 line-clamp-2 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{item.description || "No description yet."}</p></div></div><Button variant="ghost" size="sm" className="justify-self-start sm:justify-self-end">Open builder<ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" /></Button></Link>)}</div>}
    </section>
  </>;
}

function Metric({ icon: Icon, label, value, help }: { icon: typeof GitBranch; label: string; value: number; help: string }) { return <div className="panel p-5"><div className="flex items-start justify-between"><div><p className="text-xs font-semibold uppercase tracking-[.12em] text-[hsl(var(--muted-foreground))]">{label}</p><p className="mt-2 text-3xl font-semibold tracking-[-.04em]">{value}</p></div><div className="grid size-10 place-items-center rounded-xl bg-[hsl(var(--surface-subtle))]"><Icon className="size-4.5" /></div></div><p className="mt-3 text-xs text-[hsl(var(--muted-foreground))]">{help}</p></div>; }
function Loading() { return <div className="flex items-center justify-center py-16 text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin" />Loading workflows…</div>; }
function message(error: unknown, fallback: string) { return error instanceof ApiClientError ? error.message : fallback; }

function CreateWorkflow({ onCancel, onCreated }: { onCancel: () => void; onCreated: (value: WorkflowTemplate) => void }) {
  const { authorizedRequest } = useAuth(); const [name, setName] = useState(""); const [description, setDescription] = useState(""); const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null);
  async function submit(event: React.FormEvent) { event.preventDefault(); setBusy(true); setError(null); try { const result = await authorizedRequest<WorkflowTemplate>("/workflow-templates", { method: "POST", body: JSON.stringify({ name, description: description || null, initialChangeNote: "Initial draft" }) }); onCreated(result); } catch (e) { setError(message(e, "Workflow could not be created.")); } finally { setBusy(false); } }
  return <section className="panel mb-6 overflow-hidden"><div className="border-b bg-[hsl(var(--primary-soft)/.55)] px-5 py-4"><p className="font-semibold">Create workflow template</p><p className="mt-1 text-xs leading-5 text-[hsl(var(--muted-foreground))]">A private draft v1 is created first. Nothing can start from it until you validate and publish it.</p></div><form onSubmit={submit} className="grid gap-4 p-5 sm:p-6 lg:grid-cols-2"><div className="space-y-2"><Label htmlFor="workflow-name">Template name</Label><Input id="workflow-name" value={name} onChange={e => setName(e.target.value)} maxLength={180} required placeholder="Meta Ads Client Onboarding" /></div><div className="space-y-2 lg:col-span-2"><Label htmlFor="workflow-description">Purpose</Label><Textarea id="workflow-description" value={description} onChange={e => setDescription(e.target.value)} maxLength={1000} placeholder="What this workflow prepares the client and team to complete." /></div>{error && <div className="lg:col-span-2"><FormMessage>{error}</FormMessage></div>}<div className="flex justify-end gap-2 lg:col-span-2"><Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button><Button disabled={busy || !name.trim()}>{busy && <Loader2 className="size-4 animate-spin" />}Create draft</Button></div></form></section>;
}
