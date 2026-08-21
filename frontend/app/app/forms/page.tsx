"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ClipboardCheck, FileText, Loader2, Plus, RefreshCw } from "lucide-react";
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
import type { FormReviewQueueItem, FormSummary } from "@/types/forms";

export default function FormsPage(){
  const {authorizedRequest,hasPermission}=useAuth();
  const canRead=hasPermission("FORM_READ")||hasPermission("FORM_MANAGE")||hasPermission("FORM_REVIEW");
  const canManage=hasPermission("FORM_MANAGE"); const canReview=hasPermission("FORM_REVIEW");
  const [forms,setForms]=useState<FormSummary[]>([]); const [queue,setQueue]=useState<FormReviewQueueItem[]>([]);
  const [loading,setLoading]=useState(true); const [busy,setBusy]=useState(false); const [error,setError]=useState<string|null>(null);
  const [name,setName]=useState(""); const [description,setDescription]=useState("");
  const load=useCallback(async()=>{setLoading(true);setError(null);try{const [list,reviews]=await Promise.all([authorizedRequest<FormSummary[]>("/forms"),canReview?authorizedRequest<FormReviewQueueItem[]>("/form-submissions?status=SUBMITTED"):Promise.resolve([])]);setForms(list);setQueue(reviews)}catch(e){setError(message(e,"Forms could not be loaded."))}finally{setLoading(false)}},[authorizedRequest,canReview]);
  useEffect(()=>{if(canRead)void load()},[canRead,load]);
  async function create(){if(!name.trim())return;setBusy(true);setError(null);try{const form=await authorizedRequest<{id:string}>("/forms",{method:"POST",body:JSON.stringify({name,description:description||null,initialChangeNote:"Initial draft"})});location.assign(`/app/forms/${form.id}`)}catch(e){setError(message(e,"Form could not be created."));setBusy(false)}}
  if(!canRead)return <PermissionState/>;
  return <><PageHeading eyebrow="Forms & questionnaires" title="Reusable forms" description="Create immutable form versions, collect client drafts, and review traceable submissions without changing historical responses." actions={<Button variant="secondary" size="icon" onClick={()=>void load()} aria-label="Refresh forms"><RefreshCw className="size-4"/></Button>}/>{error&&<div className="mb-5"><FormMessage>{error}</FormMessage></div>}
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
      <section className="panel overflow-hidden"><div className="flex items-center justify-between border-b p-5"><div><h2 className="font-semibold">Form library</h2><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Published versions are immutable and safe to snapshot into workflows.</p></div><Badge variant="neutral">{forms.length}</Badge></div>{loading?<Loading/>:forms.length===0?<EmptyState icon={FileText} title="No forms yet" description="Create a reusable questionnaire to connect to FORM workflow steps."/>:<div className="divide-y">{forms.map(form=><Link key={form.id} href={`/app/forms/${form.id}`} className="block p-5 transition hover:bg-[hsl(var(--surface-subtle))]"><div className="flex items-start justify-between gap-4"><div><div className="flex flex-wrap items-center gap-2"><p className="font-semibold">{form.name}</p><StatusBadge status={form.status}/></div><p className="mt-1 line-clamp-2 text-sm text-[hsl(var(--muted-foreground))]">{form.description||"No description."}</p></div><div className="text-right text-xs text-[hsl(var(--muted-foreground))]"><p>{form.latestPublishedVersion?`Published v${form.latestPublishedVersion}`:"Not published"}</p>{form.draftVersion&&<p className="mt-1 text-[hsl(var(--warning))]">Draft v{form.draftVersion}</p>}</div></div></Link>)}</div>}</section>
      <div className="space-y-6">{canManage&&<section className="panel p-5"><div className="flex items-center gap-2"><Plus className="size-4"/><h2 className="font-semibold">New form</h2></div><div className="mt-4 space-y-4"><div className="space-y-2"><Label>Name</Label><Input value={name} onChange={e=>setName(e.target.value)} maxLength={180} placeholder="Project discovery questionnaire"/></div><div className="space-y-2"><Label>Description</Label><Textarea value={description} onChange={e=>setDescription(e.target.value)} maxLength={1000} rows={4}/></div><Button className="w-full" disabled={busy||!name.trim()} onClick={()=>void create()}>{busy?<Loader2 className="size-4 animate-spin"/>:<Plus className="size-4"/>}Create draft</Button></div></section>}
      {canReview&&<section className="panel overflow-hidden"><div className="border-b p-5"><div className="flex items-center gap-2"><ClipboardCheck className="size-4"/><h2 className="font-semibold">Waiting for review</h2></div><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Submitted client questionnaires only.</p></div>{queue.length===0?<p className="p-5 text-sm text-[hsl(var(--muted-foreground))]">Nothing is waiting for review.</p>:<div className="divide-y">{queue.slice(0,8).map(item=><Link className="block p-4 hover:bg-[hsl(var(--surface-subtle))]" href={`/app/form-submissions/${item.submissionId}`} key={item.submissionId}><p className="text-sm font-medium">{item.stepName}</p><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">{item.formName} · attempt {item.submissionNumber}</p></Link>)}</div>}</section>}</div>
    </div></>;
}
function Loading(){return <div className="grid min-h-48 place-items-center text-sm text-[hsl(var(--muted-foreground))]"><span className="flex items-center gap-2"><Loader2 className="size-4 animate-spin"/>Loading forms…</span></div>}
function message(error:unknown,fallback:string){return error instanceof ApiClientError?error.message:fallback}
