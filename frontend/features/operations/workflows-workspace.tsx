"use client";

import { ArrowRight, GitBranch, Plus } from "lucide-react";
import Link from "next/link";
import { type FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { Failure, OperationsHeader, PendingRows, errorMessage } from "./operations-pages";
import { operationsApi } from "./operations-api";
import type { ServiceDefinition, WorkflowTemplate } from "./types";

export function WorkflowsWorkspace() {
  const user = useCurrentUser();
  const [items, setItems] = useState<WorkflowTemplate[]>();
  const [services, setServices] = useState<ServiceDefinition[]>([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const router = useRouter();
  const canManage = user.permissions.includes("WORKFLOW_MANAGE");
  const load = () => { setError(""); Promise.all([operationsApi.templates(), operationsApi.services()]).then(([templates, serviceData]) => { setItems(templates); setServices(serviceData.filter((service) => service.status === "ACTIVE")); }).catch((cause) => setError(errorMessage(cause))); };
  useEffect(() => { Promise.all([operationsApi.templates(), operationsApi.services()]).then(([templates, serviceData]) => { setItems(templates); setServices(serviceData.filter((service) => service.status === "ACTIVE")); }).catch((cause) => setError(errorMessage(cause))); }, []);

  async function create(event: FormEvent) {
    event.preventDefault(); setPending(true); setError("");
    try { const created = await operationsApi.createTemplate({ name, description, serviceId: serviceId || undefined }); router.push(`/app/workflows/${created.template.id}`); }
    catch (cause) { setError(errorMessage(cause)); setPending(false); }
  }

  return <><OperationsHeader eyebrow="Workflow engine" title="Workflow templates" description="Build validated, versioned onboarding graphs. Published versions are immutable and every onboarding receives its own snapshot." />
    {canManage && <Card className="mt-8"><div className="flex items-center gap-3"><GitBranch aria-hidden="true" className="size-5 text-primary" /><h2 className="text-lg font-bold">Create template</h2></div><form className="mt-5 grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_14rem_auto] lg:items-end" onSubmit={create}><FormField label="Template name" htmlFor="workflow-name"><Input id="workflow-name" required maxLength={180} value={name} onChange={(event) => setName(event.target.value)} /></FormField><FormField label="Description" htmlFor="workflow-description"><Input id="workflow-description" maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)} /></FormField><div><label htmlFor="workflow-service" className="mb-2 block text-sm font-semibold">Service scope</label><select id="workflow-service" className="h-12 w-full cursor-pointer rounded-md border bg-background px-3.5 text-base" value={serviceId} onChange={(event) => setServiceId(event.target.value)}><option value="">All services</option>{services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select></div><Button type="submit" disabled={pending}><Plus aria-hidden="true" />{pending ? "Creating…" : "Create template"}</Button></form></Card>}
    {error ? <Failure message={error} retry={load} /> : !items ? <PendingRows label="Loading workflow templates" /> : items.length === 0 ? <Card className="mt-7"><h2 className="font-bold">No workflow templates</h2><p className="mt-2 text-sm text-muted-foreground">Create a template, configure a draft version, then publish it before starting onboarding.</p></Card> : <div className="mt-7 overflow-hidden rounded-xl border bg-card">{items.map((template) => <div key={template.id} className="grid gap-3 border-b px-5 py-4 last:border-b-0 md:grid-cols-[minmax(0,1fr)_11rem_auto] md:items-center"><div className="min-w-0"><p className="[overflow-wrap:anywhere] font-semibold">{template.name}</p><p className="mt-1 text-sm text-muted-foreground">{template.description || "No description"}</p></div><Badge tone={template.status === "ACTIVE" ? "success" : "neutral"}>{template.status}</Badge><Link href={`/app/workflows/${template.id}`} className="inline-flex min-h-11 items-center gap-2 rounded-md px-3 text-sm font-semibold text-primary hover:bg-muted">Open builder <ArrowRight aria-hidden="true" className="size-4" /></Link></div>)}</div>}
    {!canManage && <Alert className="mt-7">You have read-only workflow access. Draft and publish actions are hidden.</Alert>}
  </>;
}
