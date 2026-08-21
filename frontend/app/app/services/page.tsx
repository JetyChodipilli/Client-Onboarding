"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Archive, BriefcaseBusiness, Loader2, Pencil, Plus, RefreshCw, Save, Search } from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { FormMessage } from "@/components/auth/form-message";
import { PageHeading } from "@/components/layout/page-heading";
import { EmptyState } from "@/components/shared/empty-state";
import { PaginationControls } from "@/components/shared/pagination-controls";
import { PermissionState } from "@/components/shared/permission-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/services/api-client";
import type { PageMeta } from "@/types/api";
import type { ServiceDefinition } from "@/types/service";

export default function ServicesPage(){
  const {authorizedRequestWithMeta,hasPermission}=useAuth();
  const canRead=hasPermission("SERVICE_READ")||hasPermission("SERVICE_MANAGE");
  const canManage=hasPermission("SERVICE_MANAGE");
  const pageSize=25;
  const [services,setServices]=useState<ServiceDefinition[]>([]);
  const [meta,setMeta]=useState<PageMeta>({page:0,size:pageSize,totalElements:0,totalPages:0});
  const [page,setPage]=useState(0);
  const [query,setQuery]=useState("");
  const [searchQuery,setSearchQuery]=useState("");
  const [loading,setLoading]=useState(true);
  const [showCreate,setShowCreate]=useState(false);
  const [editing,setEditing]=useState<string|null>(null);
  const [error,setError]=useState<string|null>(null);
  const loadSequence=useRef(0);

  useEffect(()=>{
    const handle=window.setTimeout(()=>{setPage(0);setSearchQuery(query.trim())},300);
    return()=>window.clearTimeout(handle);
  },[query]);

  const load=useCallback(async()=>{
    const sequence=++loadSequence.current;
    setLoading(true);setError(null);
    try{
      const params=new URLSearchParams({page:String(page),size:String(pageSize)});
      if(searchQuery)params.set("query",searchQuery);
      const result=await authorizedRequestWithMeta<ServiceDefinition[],PageMeta>(`/services?${params}`);
      if(sequence!==loadSequence.current)return;
      if(result.meta.totalPages>0&&page>=result.meta.totalPages){setPage(result.meta.totalPages-1);return}
      setServices(result.data);setMeta(result.meta);
    }catch(requestError){
      if(sequence!==loadSequence.current)return;
      setError(message(requestError,"Services could not be loaded."));
    }finally{if(sequence===loadSequence.current)setLoading(false)}
  },[authorizedRequestWithMeta,page,searchQuery]);

  useEffect(()=>{if(canRead)void load()},[canRead,load]);
  if(!canRead)return <PermissionState/>;
  return <>
    <PageHeading eyebrow="Service catalog" title="Services" description="Define reusable service types without truncating large catalogs. Archiving prevents new assignments while preserving existing project references." actions={<div className="flex gap-2"><Button variant="secondary" size="icon" onClick={()=>void load()} aria-label="Refresh services"><RefreshCw className="size-4"/></Button>{canManage&&<Button onClick={()=>setShowCreate(v=>!v)}><Plus className="size-4"/>New service</Button>}</div>}/>
    {showCreate&&<ServiceEditor onDone={()=>{setShowCreate(false);if(page!==0)setPage(0);else void load()}} onCancel={()=>setShowCreate(false)}/>} {error&&<div className="mb-5"><FormMessage>{error}</FormMessage></div>}
    <section className="panel overflow-hidden">
      <div className="border-b p-4 sm:p-5">
        <label className="relative block max-w-xl"><span className="sr-only">Search services</span><Search className="pointer-events-none absolute left-3.5 top-3.5 size-4 text-[hsl(var(--muted-foreground))]"/><Input className="pl-10" value={query} maxLength={120} onChange={e=>setQuery(e.target.value)} placeholder="Search service name or code…"/></label>
        <p className="mt-2 text-xs text-[hsl(var(--muted-foreground))]">{meta.totalElements} services match this view</p>
      </div>
      {loading?<Loading/>:services.length===0?<EmptyState title={searchQuery?"No matching services":"No services configured"} description={searchQuery?"Try a broader service name or code prefix.":"Create at least one active service before a project can be created."}/>:<div className="divide-y">{services.map(service=>editing===service.id?<ServiceEditor key={service.id} service={service} onDone={()=>{setEditing(null);void load()}} onCancel={()=>setEditing(null)}/>:<ServiceRow key={service.id} service={service} canManage={canManage} onEdit={()=>setEditing(service.id)} onArchived={()=>void load()} onError={setError}/>)}</div>}
      <PaginationControls meta={meta} disabled={loading} onPageChange={setPage}/>
    </section>
  </>
}

function ServiceRow({service,canManage,onEdit,onArchived,onError}:{service:ServiceDefinition;canManage:boolean;onEdit:()=>void;onArchived:()=>void;onError:(value:string|null)=>void}){const{authorizedRequest}=useAuth();const[busy,setBusy]=useState(false);async function archive(){if(!confirm(`Archive ${service.name}? It will no longer be assignable to new projects.`))return;setBusy(true);onError(null);try{await authorizedRequest(`/services/${service.id}/archive`,{method:"POST",body:JSON.stringify({version:service.version})});onArchived()}catch(error){onError(message(error,"Service could not be archived."))}finally{setBusy(false)}}return <article className="grid gap-4 px-5 py-5 md:grid-cols-[auto_1fr_auto] md:items-center"><div className="grid size-11 place-items-center rounded-xl bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]"><BriefcaseBusiness className="size-5"/></div><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h2 className="font-semibold">{service.name}</h2><StatusBadge status={service.status}/><span className="rounded-md bg-[hsl(var(--surface-subtle))] px-2 py-1 font-mono text-[11px] text-[hsl(var(--muted-foreground))]">{service.code}</span></div><p className="mt-1 max-w-3xl text-sm leading-6 text-[hsl(var(--muted-foreground))]">{service.description||"No description provided."}</p></div>{canManage&&service.status!=="ARCHIVED"&&<div className="flex gap-2"><Button variant="ghost" size="sm" onClick={onEdit}><Pencil className="size-4"/>Edit</Button><Button variant="ghost" size="sm" onClick={()=>void archive()} disabled={busy}><Archive className="size-4"/>Archive</Button></div>}</article>}

function ServiceEditor({service,onDone,onCancel}:{service?:ServiceDefinition;onDone:()=>void;onCancel:()=>void}){const{authorizedRequest}=useAuth();const[code,setCode]=useState(service?.code??"");const[name,setName]=useState(service?.name??"");const[description,setDescription]=useState(service?.description??"");const[busy,setBusy]=useState(false);const[error,setError]=useState<string|null>(null);async function save(event:React.FormEvent){event.preventDefault();setBusy(true);setError(null);try{await authorizedRequest(service?`/services/${service.id}`:"/services",{method:service?"PATCH":"POST",body:JSON.stringify(service?{name,description:description||null,version:service.version}:{code,name,description:description||null})});onDone()}catch(requestError){setError(message(requestError,"Service could not be saved."))}finally{setBusy(false)}}return <form onSubmit={save} className="border-b bg-[hsl(var(--surface-subtle))] p-5 sm:p-6"><div className="mb-5"><p className="text-sm font-semibold">{service?"Edit service":"Create service"}</p><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Service codes are stable identifiers. The server normalizes new codes for consistency.</p></div><div className="grid gap-4 md:grid-cols-[.65fr_1fr]"><div className="space-y-2"><Label htmlFor={`service-code-${service?.id??"new"}`}>Service code</Label><Input id={`service-code-${service?.id??"new"}`} value={code} onChange={e=>setCode(e.target.value)} disabled={Boolean(service)} required={!service} placeholder="META_ADS" maxLength={80}/></div><div className="space-y-2"><Label htmlFor={`service-name-${service?.id??"new"}`}>Name</Label><Input id={`service-name-${service?.id??"new"}`} value={name} onChange={e=>setName(e.target.value)} required maxLength={160}/></div><div className="space-y-2 md:col-span-2"><Label htmlFor={`service-desc-${service?.id??"new"}`}>Description</Label><Textarea id={`service-desc-${service?.id??"new"}`} value={description} onChange={e=>setDescription(e.target.value)} maxLength={1000} placeholder="What this service covers and when teams should choose it."/></div></div>{error&&<div className="mt-4"><FormMessage>{error}</FormMessage></div>}<div className="mt-4 flex justify-end gap-2"><Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button><Button disabled={busy||!name.trim()||(!service&&!code.trim())}>{busy?<Loader2 className="size-4 animate-spin"/>:<Save className="size-4"/>}{service?"Save changes":"Create service"}</Button></div></form>}
function Loading(){return <div className="flex items-center justify-center py-16 text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin"/>Loading service catalog…</div>}
function message(error:unknown,fallback:string){return error instanceof ApiClientError?error.message:fallback}
