"use client";

import { ArrowRight, BriefcaseBusiness, Plus, RefreshCw, Search, Settings2 } from "lucide-react";
import Link from "next/link";
import { type FormEvent, useEffect, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { ApiClientError } from "@/lib/api-client";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { operationsApi } from "./operations-api";
import type { Client, Project, ServiceDefinition } from "./types";

export function OperationsHeader({ eyebrow, title, description, action }: {
  eyebrow: string;
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return <div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-bold uppercase tracking-[0.15em] text-primary">{eyebrow}</p><h1 className="mt-3 text-3xl font-bold tracking-[-0.035em] sm:text-4xl">{title}</h1><p className="mt-3 max-w-2xl text-muted-foreground">{description}</p></div>{action}</div>;
}

export function PendingRows({ label = "Loading records" }: { label?: string }) {
  return <div className="mt-8 rounded-xl border bg-card p-5" aria-busy="true" aria-label={label}><p className="text-sm text-muted-foreground">{label}…</p><div className="mt-5 space-y-3">{Array.from({ length: 3 }, (_, index) => <div key={index} className="h-16 animate-pulse rounded-lg bg-muted" />)}</div></div>;
}

export function Failure({ message, retry }: { message: string; retry: () => void }) {
  return <div className="mt-7"><Alert tone="error">{message}</Alert><Button variant="outline" className="mt-4" onClick={retry}><RefreshCw aria-hidden="true" />Retry</Button></div>;
}

export const errorMessage = (cause: unknown) => cause instanceof ApiClientError
  ? `${cause.message}${cause.requestId ? ` Request ${cause.requestId}.` : ""}`
  : "The request could not be completed. Check the connection and try again.";

const selectClass = "h-12 w-full cursor-pointer rounded-md border bg-background px-3.5 text-base text-foreground";

export function ClientsWorkspace() {
  const user = useCurrentUser();
  const [items, setItems] = useState<Client[]>();
  const [search, setSearch] = useState("");
  const [name, setName] = useState("");
  const [status, setStatus] = useState<Client["status"]>("PROSPECT");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const canCreate = user.permissions.includes("CLIENT_CREATE");
  const load = (query = search) => { setError(""); operationsApi.clients(query).then(setItems).catch((cause) => setError(errorMessage(cause))); };
  useEffect(() => { operationsApi.clients("").then(setItems).catch((cause) => setError(errorMessage(cause))); }, []);
  async function create(event: FormEvent) { event.preventDefault(); setPending(true); setError(""); try { await operationsApi.createClient({ name, status }); setName(""); setStatus("PROSPECT"); load(""); } catch (cause) { setError(errorMessage(cause)); } finally { setPending(false); } }
  return <><OperationsHeader eyebrow="Portfolio" title="Clients" description="Tenant-scoped client records, kept separate from contacts, login identities, and project onboarding state." />
    {canCreate && <Card className="mt-8"><h2 className="text-lg font-bold">Create client</h2><form className="mt-5 grid gap-4 sm:grid-cols-[minmax(0,1fr)_12rem_auto] sm:items-end" onSubmit={create}><FormField label="Client name" htmlFor="client-name"><Input id="client-name" required maxLength={160} value={name} onChange={(event) => setName(event.target.value)} /></FormField><div><label htmlFor="client-status" className="mb-2 block text-sm font-semibold">Status</label><select id="client-status" className={selectClass} value={status} onChange={(event) => setStatus(event.target.value as Client["status"])}><option>PROSPECT</option><option>ACTIVE</option><option>INACTIVE</option></select></div><Button type="submit" disabled={pending}><Plus aria-hidden="true" />{pending ? "Creating…" : "Create client"}</Button></form></Card>}
    <form className="mt-6 flex gap-3" onSubmit={(event) => { event.preventDefault(); load(); }} role="search"><label htmlFor="client-search" className="sr-only">Search clients</label><Input id="client-search" type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search client or legal name" /><Button variant="outline" type="submit"><Search aria-hidden="true" />Search</Button></form>
    {error ? <Failure message={error} retry={() => load()} /> : !items ? <PendingRows label="Loading clients" /> : items.length === 0 ? <Card className="mt-7"><h2 className="font-bold">No clients found</h2><p className="mt-2 text-sm text-muted-foreground">Create a client or clear the search to see the portfolio.</p></Card> : <div className="mt-7 overflow-hidden rounded-xl border bg-card">{items.map((client) => <div key={client.id} className="grid gap-3 border-b px-5 py-4 last:border-b-0 sm:grid-cols-[minmax(0,1fr)_9rem_auto] sm:items-center"><div className="min-w-0"><p className="overflow-wrap-anywhere font-semibold">{client.name}</p><p className="mt-1 text-sm text-muted-foreground">{client.legalName || client.email || "No secondary details"}</p></div><Badge tone={client.status === "ACTIVE" ? "success" : client.status === "PROSPECT" ? "info" : "neutral"}>{client.status}</Badge><Link href={`/app/clients/${client.id}`} className="inline-flex min-h-11 items-center gap-2 rounded-md px-3 text-sm font-semibold text-primary hover:bg-muted">Open <ArrowRight aria-hidden="true" className="size-4" /></Link></div>)}</div>}
  </>;
}

export function ServicesWorkspace() {
  const user = useCurrentUser();
  const [items, setItems] = useState<ServiceDefinition[]>();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const canManage = user.permissions.includes("SERVICE_MANAGE");
  const load = () => { setError(""); operationsApi.services().then(setItems).catch((cause) => setError(errorMessage(cause))); };
  useEffect(() => { operationsApi.services().then(setItems).catch((cause) => setError(errorMessage(cause))); }, []);
  async function create(event: FormEvent) { event.preventDefault(); setPending(true); setError(""); try { await operationsApi.createService({ code, name, status: "ACTIVE" }); setCode(""); setName(""); load(); } catch (cause) { setError(errorMessage(cause)); } finally { setPending(false); } }
  return <><OperationsHeader eyebrow="Catalog" title="Services" description="Service types scope projects and determine which workflow templates are applicable." />
    {canManage && <Card className="mt-8"><h2 className="text-lg font-bold">Add service</h2><form className="mt-5 grid gap-4 md:grid-cols-[14rem_minmax(0,1fr)_auto] md:items-end" onSubmit={create}><FormField label="Code" htmlFor="service-code" hint="Stable key, for example META_ADS."><Input id="service-code" required maxLength={80} value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} /></FormField><FormField label="Service name" htmlFor="service-name"><Input id="service-name" required maxLength={160} value={name} onChange={(event) => setName(event.target.value)} /></FormField><Button type="submit" disabled={pending}><Plus aria-hidden="true" />{pending ? "Adding…" : "Add service"}</Button></form></Card>}
    {error ? <Failure message={error} retry={load} /> : !items ? <PendingRows label="Loading services" /> : items.length === 0 ? <Card className="mt-7"><h2 className="font-bold">No services configured</h2><p className="mt-2 text-sm text-muted-foreground">Add the first service before creating a project or scoped workflow.</p></Card> : <div className="mt-7 grid gap-4 md:grid-cols-2">{items.map((service) => <Card key={service.id}><div className="flex items-start justify-between gap-4"><div className="min-w-0"><p className="font-mono text-xs font-bold text-primary">{service.code}</p><h2 className="mt-2 overflow-wrap-anywhere text-lg font-bold">{service.name}</h2><p className="mt-2 text-sm text-muted-foreground">{service.description || "No description"}</p></div><Badge tone={service.status === "ACTIVE" ? "success" : "neutral"}>{service.status}</Badge></div></Card>)}</div>}
  </>;
}

export function ProjectsWorkspace() {
  const user = useCurrentUser();
  const [projects, setProjects] = useState<Project[]>();
  const [clients, setClients] = useState<Client[]>([]);
  const [services, setServices] = useState<ServiceDefinition[]>([]);
  const [clientId, setClientId] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const canCreate = user.permissions.includes("PROJECT_CREATE");
  const load = () => { setError(""); Promise.all([operationsApi.projects(), operationsApi.clients(), operationsApi.services()]).then(([projectData, clientData, serviceData]) => { setProjects(projectData); setClients(clientData.filter((item) => !item.archivedAt)); setServices(serviceData.filter((item) => item.status === "ACTIVE")); setClientId((value) => value || clientData.find((item) => !item.archivedAt)?.id || ""); setServiceId((value) => value || serviceData.find((item) => item.status === "ACTIVE")?.id || ""); }).catch((cause) => setError(errorMessage(cause))); };
  useEffect(() => { Promise.all([operationsApi.projects(), operationsApi.clients(), operationsApi.services()]).then(([projectData, clientData, serviceData]) => { setProjects(projectData); setClients(clientData.filter((item) => !item.archivedAt)); setServices(serviceData.filter((item) => item.status === "ACTIVE")); setClientId(clientData.find((item) => !item.archivedAt)?.id || ""); setServiceId(serviceData.find((item) => item.status === "ACTIVE")?.id || ""); }).catch((cause) => setError(errorMessage(cause))); }, []);
  async function create(event: FormEvent) { event.preventDefault(); setPending(true); setError(""); try { await operationsApi.createProject({ clientId, serviceId, name }); setName(""); load(); } catch (cause) { setError(errorMessage(cause)); } finally { setPending(false); } }
  return <><OperationsHeader eyebrow="Delivery portfolio" title="Projects" description="Projects own delivery lifecycle; payment, contract, onboarding, and step states remain separate." />
    {canCreate && <Card className="mt-8"><div className="flex items-center gap-3"><Settings2 aria-hidden="true" className="size-5 text-primary" /><h2 className="text-lg font-bold">Create draft project</h2></div><form className="mt-5 grid gap-4 lg:grid-cols-4 lg:items-end" onSubmit={create}><FormField label="Project name" htmlFor="project-name"><Input id="project-name" required maxLength={180} value={name} onChange={(event) => setName(event.target.value)} /></FormField><div><label className="mb-2 block text-sm font-semibold" htmlFor="project-client">Client</label><select id="project-client" required className={selectClass} value={clientId} onChange={(event) => setClientId(event.target.value)}>{clients.map((client) => <option key={client.id} value={client.id}>{client.name}</option>)}</select></div><div><label className="mb-2 block text-sm font-semibold" htmlFor="project-service">Service</label><select id="project-service" required className={selectClass} value={serviceId} onChange={(event) => setServiceId(event.target.value)}>{services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select></div><Button type="submit" disabled={pending || !clientId || !serviceId}><BriefcaseBusiness aria-hidden="true" />{pending ? "Creating…" : "Create project"}</Button></form></Card>}
    {error ? <Failure message={error} retry={load} /> : !projects ? <PendingRows label="Loading projects" /> : projects.length === 0 ? <Card className="mt-7"><h2 className="font-bold">No projects yet</h2><p className="mt-2 text-sm text-muted-foreground">Create a draft project after at least one client and active service exist.</p></Card> : <div className="mt-7 overflow-hidden rounded-xl border bg-card">{projects.map((project) => <div key={project.id} className="grid gap-3 border-b px-5 py-4 last:border-b-0 md:grid-cols-[minmax(0,1fr)_11rem_9rem_auto] md:items-center"><div className="min-w-0"><p className="overflow-wrap-anywhere font-semibold">{project.name}</p><p className="mt-1 text-sm text-muted-foreground">{project.clientName} · {project.serviceName}</p></div><span className="font-mono text-xs font-semibold text-muted-foreground">{project.serviceCode}</span><Badge tone={project.status === "DRAFT" ? "info" : project.status === "COMPLETED" ? "success" : "neutral"}>{project.status}</Badge><Link href={`/app/projects/${project.id}`} className="inline-flex min-h-11 items-center gap-2 rounded-md px-3 text-sm font-semibold text-primary hover:bg-muted">Open <ArrowRight aria-hidden="true" className="size-4" /></Link></div>)}</div>}
  </>;
}
