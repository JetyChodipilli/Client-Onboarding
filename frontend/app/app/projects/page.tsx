"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowRight, FolderKanban, Loader2, Plus, RefreshCw, Search } from "lucide-react";
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
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/services/api-client";
import type { PageMeta } from "@/types/api";
import type { Client } from "@/types/client";
import type { Project, ProjectStatus } from "@/types/project";
import type { ServiceDefinition } from "@/types/service";

const PAGE_SIZE = 25;
const EMPTY_META: PageMeta = { page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };
const statusOptions: Array<ProjectStatus | "ALL"> = ["ALL", "DRAFT", "ONBOARDING", "READY", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED"];

export default function ProjectsPage(){
  const {authorizedRequestWithMeta,hasPermission}=useAuth();
  const canRead=hasPermission("PROJECT_READ");
  const canCreate=hasPermission("PROJECT_CREATE");
  const canSelect=hasPermission("CLIENT_READ")&&(hasPermission("SERVICE_READ")||hasPermission("SERVICE_MANAGE"));
  const[projects,setProjects]=useState<Project[]>([]);
  const[meta,setMeta]=useState<PageMeta>(EMPTY_META);
  const[page,setPage]=useState(0);
  const[loading,setLoading]=useState(true);
  const[showCreate,setShowCreate]=useState(false);
  const[status,setStatus]=useState<ProjectStatus|"ALL">("ALL");
  const[query,setQuery]=useState("");
  const[searchQuery,setSearchQuery]=useState("");
  const[error,setError]=useState<string|null>(null);
  const loadSequence=useRef(0);

  useEffect(()=>{
    const handle=window.setTimeout(()=>{setPage(0);setSearchQuery(query.trim())},300);
    return()=>window.clearTimeout(handle);
  },[query]);

  const load=useCallback(async()=>{
    const sequence=++loadSequence.current;
    setLoading(true);setError(null);
    try{
      const params=new URLSearchParams({page:String(page),size:String(PAGE_SIZE)});
      if(status!=="ALL")params.set("status",status);
      if(searchQuery)params.set("query",searchQuery);
      const result=await authorizedRequestWithMeta<Project[],PageMeta>(`/projects?${params}`);
      if(sequence!==loadSequence.current)return;
      if(result.meta.totalPages>0&&page>=result.meta.totalPages){setPage(result.meta.totalPages-1);return}
      setProjects(result.data);setMeta(result.meta);
    }catch(requestError){
      if(sequence!==loadSequence.current)return;
      setError(message(requestError,"Projects could not be loaded."));
    }finally{if(sequence===loadSequence.current)setLoading(false)}
  },[authorizedRequestWithMeta,page,searchQuery,status]);

  useEffect(()=>{if(canRead)void load()},[canRead,load]);
  if(!canRead)return <PermissionState/>;

  return <>
    <PageHeading eyebrow="Delivery portfolio" title="Projects" description="A project connects one client to one service and owns its own lifecycle. Payment, contract, and onboarding states remain separate." actions={<div className="flex gap-2"><Button variant="secondary" size="icon" onClick={()=>void load()} aria-label="Refresh projects"><RefreshCw className="size-4"/></Button>{canCreate&&<Button onClick={()=>setShowCreate(v=>!v)}><Plus className="size-4"/>New project</Button>}</div>}/>
    {showCreate&&(canSelect?<ProjectCreator onDone={()=>{setShowCreate(false);if(page!==0)setPage(0);else void load()}} onCancel={()=>setShowCreate(false)}/>:<div className="panel mb-6 p-5"><FormMessage>Creating a project also requires permission to read clients and the service catalog so relationships can be selected safely.</FormMessage></div>)}
    {error&&<div className="mb-5"><FormMessage>{error}</FormMessage></div>}
    <section className="panel overflow-hidden">
      <div className="grid gap-3 border-b p-4 sm:grid-cols-[1fr_13rem] sm:p-5">
        <label className="relative"><span className="sr-only">Search projects</span><Search className="pointer-events-none absolute left-3.5 top-3.5 size-4 text-[hsl(var(--muted-foreground))]"/><Input className="pl-10" value={query} maxLength={120} onChange={e=>setQuery(e.target.value)} placeholder="Search project, client, or service…"/></label>
        <Select value={status} onChange={e=>{setStatus(e.target.value as ProjectStatus|"ALL");setPage(0)}} aria-label="Filter project status">{statusOptions.map(option=><option key={option} value={option}>{option==="ALL"?"All active records":option.replaceAll("_"," ")}</option>)}</Select>
      </div>
      {loading?<Loading/>:projects.length===0?<EmptyState title={searchQuery?"No matching projects":"No projects yet"} description={searchQuery?"Try a broader project, client, or service-name prefix, or another status filter.":"Create a project after the client and service have been configured."}/>:<div className="overflow-x-auto"><table className="data-table"><thead><tr><th>Project</th><th>Client</th><th>Service</th><th>Status</th><th className="w-32">Open</th></tr></thead><tbody>{projects.map(project=><tr key={project.id}><td><div className="flex items-center gap-3"><div className="grid size-10 place-items-center rounded-xl bg-[hsl(var(--surface-subtle))]"><FolderKanban className="size-4.5 text-[hsl(var(--muted-foreground))]"/></div><div><p className="font-semibold">{project.name}</p><p className="mt-0.5 text-xs text-[hsl(var(--muted-foreground))]">Updated {formatDate(project.updatedAt)}</p></div></div></td><td>{project.clientName}</td><td>{project.serviceName}</td><td><StatusBadge status={project.status}/></td><td><Button asChild variant="ghost" size="sm"><Link href={`/app/projects/${project.id}`}>Open<ArrowRight className="size-4"/></Link></Button></td></tr>)}</tbody></table></div>}
      <PaginationControls meta={meta} disabled={loading} onPageChange={setPage}/>
    </section>
  </>
}

function ProjectCreator({onDone,onCancel}:{onDone:()=>void;onCancel:()=>void}){
  const{authorizedRequest}=useAuth();
  const[clients,setClients]=useState<Client[]>([]);const[services,setServices]=useState<ServiceDefinition[]>([]);
  const[clientSearch,setClientSearch]=useState("");const[serviceSearch,setServiceSearch]=useState("");
  const[clientId,setClientId]=useState("");const[serviceId,setServiceId]=useState("");
  const[selectedClientName,setSelectedClientName]=useState("");const[selectedServiceName,setSelectedServiceName]=useState("");
  const[name,setName]=useState("");const[description,setDescription]=useState("");
  const[busy,setBusy]=useState(false);const[clientOptionsBusy,setClientOptionsBusy]=useState(false);const[serviceOptionsBusy,setServiceOptionsBusy]=useState(false);const[error,setError]=useState<string|null>(null);

  useEffect(()=>{
    let active=true;const handle=window.setTimeout(async()=>{setClientOptionsBusy(true);try{
      const params=new URLSearchParams({size:"50"});if(clientSearch.trim())params.set("query",clientSearch.trim());
      const rows=await authorizedRequest<Client[]>(`/clients?${params}`);if(active)setClients(rows.filter(c=>c.status!=="ARCHIVED"));
    }catch(requestError){if(active)setError(message(requestError,"Clients could not be searched."))}finally{if(active)setClientOptionsBusy(false)}},250);
    return()=>{active=false;window.clearTimeout(handle)};
  },[authorizedRequest,clientSearch]);

  useEffect(()=>{
    let active=true;const handle=window.setTimeout(async()=>{setServiceOptionsBusy(true);try{
      const params=new URLSearchParams({size:"50"});if(serviceSearch.trim())params.set("query",serviceSearch.trim());
      const rows=await authorizedRequest<ServiceDefinition[]>(`/services?${params}`);if(active)setServices(rows.filter(s=>s.status==="ACTIVE"));
    }catch(requestError){if(active)setError(message(requestError,"Services could not be searched."))}finally{if(active)setServiceOptionsBusy(false)}},250);
    return()=>{active=false;window.clearTimeout(handle)};
  },[authorizedRequest,serviceSearch]);

  async function save(event:React.FormEvent){event.preventDefault();setBusy(true);setError(null);try{await authorizedRequest<Project>("/projects",{method:"POST",body:JSON.stringify({clientId,serviceId,name,description:description||null})});onDone()}catch(requestError){setError(message(requestError,"Project could not be created."))}finally{setBusy(false)}}

  const clientHasSelected=clients.some(client=>client.id===clientId);const serviceHasSelected=services.some(service=>service.id===serviceId);
  return <section className="panel mb-6 overflow-hidden"><div className="border-b px-5 py-4"><p className="font-semibold">Create a project</p><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Searchable relationship selectors avoid silently truncating large client or service catalogs.</p></div><form onSubmit={save} className="grid gap-4 p-5 sm:p-6 lg:grid-cols-2">
    <div className="space-y-2"><Label htmlFor="project-client-search">Find client</Label><Input id="project-client-search" value={clientSearch} maxLength={120} onChange={e=>setClientSearch(e.target.value)} placeholder="Start typing a client name"/><Label htmlFor="project-client">Client</Label><Select id="project-client" value={clientId} onChange={e=>{setClientId(e.target.value);setSelectedClientName(e.currentTarget.selectedOptions[0]?.text??"")}} required><option value="">Choose a client</option>{clientId&&!clientHasSelected&&<option value={clientId}>{selectedClientName||"Selected client"}</option>}{clients.map(client=><option key={client.id} value={client.id}>{client.name}</option>)}</Select></div>
    <div className="space-y-2"><Label htmlFor="project-service-search">Find service</Label><Input id="project-service-search" value={serviceSearch} maxLength={120} onChange={e=>setServiceSearch(e.target.value)} placeholder="Start typing a service name or code"/><Label htmlFor="project-service">Service</Label><Select id="project-service" value={serviceId} onChange={e=>{setServiceId(e.target.value);setSelectedServiceName(e.currentTarget.selectedOptions[0]?.text??"")}} required><option value="">Choose a service</option>{serviceId&&!serviceHasSelected&&<option value={serviceId}>{selectedServiceName||"Selected service"}</option>}{services.map(service=><option key={service.id} value={service.id}>{service.name}</option>)}</Select></div>
    <div className="space-y-2 lg:col-span-2"><Label htmlFor="project-name">Project name</Label><Input id="project-name" value={name} onChange={e=>setName(e.target.value)} required maxLength={180} placeholder="Q4 Growth Campaign"/></div><div className="space-y-2 lg:col-span-2"><Label htmlFor="project-description">Delivery context</Label><Textarea id="project-description" value={description} onChange={e=>setDescription(e.target.value)} maxLength={2000} placeholder="Short internal context for this project."/></div>{(clientOptionsBusy||serviceOptionsBusy)&&<p className="text-xs text-[hsl(var(--muted-foreground))] lg:col-span-2"><Loader2 className="mr-1 inline size-3.5 animate-spin"/>Refreshing relationship options…</p>}{error&&<div className="lg:col-span-2"><FormMessage>{error}</FormMessage></div>}<div className="flex justify-end gap-2 lg:col-span-2"><Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button><Button disabled={busy||!clientId||!serviceId||!name.trim()}>{busy&&<Loader2 className="size-4 animate-spin"/>}Create project</Button></div></form></section>
}
function Loading(){return <div className="flex items-center justify-center py-16 text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin"/>Loading projects…</div>}
function formatDate(value:string){return new Intl.DateTimeFormat(undefined,{dateStyle:"medium"}).format(new Date(value))}
function message(error:unknown,fallback:string){return error instanceof ApiClientError?error.message:fallback}
