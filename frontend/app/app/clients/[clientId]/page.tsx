"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, Archive, Loader2, Mail, Pencil, Phone, Plus, Save, UserRound } from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { FormMessage } from "@/components/auth/form-message";
import { PageHeading } from "@/components/layout/page-heading";
import { EmptyState } from "@/components/shared/empty-state";
import { PermissionState } from "@/components/shared/permission-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { ApiClientError } from "@/services/api-client";
import type { ActivityItem } from "@/types/activity";
import type { Client, ClientContact, ClientStatus } from "@/types/client";

export default function ClientDetailPage() {
  const params = useParams<{ clientId: string }>();
  const router = useRouter();
  const { authorizedRequest, hasPermission } = useAuth();
  const canRead = hasPermission("CLIENT_READ");
  const canUpdate = hasPermission("CLIENT_UPDATE");
  const [client, setClient] = useState<Client | null>(null);
  const [contacts, setContacts] = useState<ClientContact[]>([]);
  const [activity, setActivity] = useState<ActivityItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingClient, setEditingClient] = useState(false);
  const [showContact, setShowContact] = useState(false);
  const [editingContactId, setEditingContactId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!params.clientId) return;
    setLoading(true); setError(null);
    try {
      const [clientRow, contactRows, activityRows] = await Promise.all([
        authorizedRequest<Client>(`/clients/${params.clientId}`),
        authorizedRequest<ClientContact[]>(`/clients/${params.clientId}/contacts?size=100`),
        authorizedRequest<ActivityItem[]>(`/clients/${params.clientId}/activity?size=100`),
      ]);
      setClient(clientRow); setContacts(contactRows); setActivity(activityRows);
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "Client details could not be loaded.");
    } finally { setLoading(false); }
  }, [authorizedRequest, params.clientId]);

  useEffect(() => { if (canRead) void load(); }, [canRead, load]);

  async function archive() {
    if (!client || !confirm(`Archive ${client.name}? Existing references will remain traceable.`)) return;
    setError(null);
    try {
      await authorizedRequest(`/clients/${client.id}/archive`, { method: "POST", body: JSON.stringify({ version: client.version }) });
      router.push("/app/clients");
    } catch (requestError) { setError(message(requestError, "Client could not be archived.")); }
  }

  if (!canRead) return <PermissionState />;
  if (loading) return <div className="panel flex min-h-64 items-center justify-center text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin"/>Loading client…</div>;
  if (!client) return <><Button asChild variant="ghost" className="mb-5"><Link href="/app/clients"><ArrowLeft className="size-4"/>Back to clients</Link></Button><div className="panel p-8">{error ? <FormMessage>{error}</FormMessage> : <EmptyState title="Client not found" description="The record may be archived or outside your organization."/>}</div></>;

  return <>
    <Button asChild variant="ghost" className="mb-5 -ml-3"><Link href="/app/clients"><ArrowLeft className="size-4"/>Back to clients</Link></Button>
    <PageHeading eyebrow="Client relationship" title={client.name} description="Company, contact, and project identities stay separate so access and business history remain precise." actions={<div className="flex gap-2">{canUpdate && client.status !== "ARCHIVED" && <Button variant="secondary" onClick={() => setEditingClient((value) => !value)}><Pencil className="size-4"/>Edit</Button>}{canUpdate && client.status !== "ARCHIVED" && <Button variant="destructive" onClick={() => void archive()}><Archive className="size-4"/>Archive</Button>}</div>}/>
    {error && <div className="mb-5"><FormMessage>{error}</FormMessage></div>}

    <div className="grid gap-6 xl:grid-cols-[.72fr_1.28fr]">
      <section className="panel self-start overflow-hidden">
        <div className="border-b px-5 py-4"><p className="font-semibold">Client profile</p><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Relationship-level details</p></div>
        {editingClient ? <ClientEditor client={client} onDone={() => { setEditingClient(false); void load(); }} onError={setError}/> : <dl className="divide-y"><Fact label="Status"><StatusBadge status={client.status}/></Fact><Fact label="Created">{formatDate(client.createdAt)}</Fact><Fact label="Last updated">{formatDate(client.updatedAt)}</Fact><Fact label="Record ID"><span className="break-all font-mono text-[11px]">{client.id}</span></Fact></dl>}
      </section>

      <section className="panel overflow-hidden">
        <div className="flex items-center justify-between gap-4 border-b px-5 py-4"><div><p className="font-semibold">Client contacts</p><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Real people at this company. Authentication identities are managed separately.</p></div>{canUpdate && client.status !== "ARCHIVED" && <Button size="sm" onClick={() => setShowContact((value) => !value)}><Plus className="size-4"/>Add contact</Button>}</div>
        {showContact && <ContactEditor clientId={client.id} onDone={() => { setShowContact(false); void load(); }} onCancel={() => setShowContact(false)}/>}
        {contacts.length === 0 ? <EmptyState title="No contacts yet" description="Add the people who represent this client. A contact does not automatically become a portal user."/> : <div className="divide-y">{contacts.map((contact) => editingContactId === contact.id ? <ContactEditor key={contact.id} contact={contact} clientId={client.id} onDone={() => { setEditingContactId(null); void load(); }} onCancel={() => setEditingContactId(null)}/> : <article key={contact.id} className="flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center"><div className="grid size-10 shrink-0 place-items-center rounded-full bg-[hsl(var(--surface-subtle))]"><UserRound className="size-4.5 text-[hsl(var(--muted-foreground))]"/></div><div className="min-w-0 flex-1"><p className="font-semibold">{contact.displayName}</p><p className="mt-0.5 text-xs text-[hsl(var(--muted-foreground))]">{contact.jobTitle || "Contact"}</p><div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-[hsl(var(--muted-foreground))]"><span className="flex items-center gap-1.5"><Mail className="size-3.5"/>{contact.email}</span>{contact.phone && <span className="flex items-center gap-1.5"><Phone className="size-3.5"/>{contact.phone}</span>}</div></div>{canUpdate && client.status !== "ARCHIVED" && <Button variant="ghost" size="sm" onClick={() => setEditingContactId(contact.id)}><Pencil className="size-4"/>Edit</Button>}</article>)}</div>}
      </section>
    </div>

    <section className="panel mt-6 overflow-hidden">
      <div className="border-b px-5 py-4"><p className="font-semibold">Relationship activity</p><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Operational history for client and contact changes in this tenant.</p></div>
      {activity.length === 0 ? <EmptyState title="No activity recorded" description="Client and contact changes will appear here as the relationship evolves."/> : <ol className="divide-y">{activity.map((item) => <li key={item.id} className="grid gap-2 px-5 py-4 sm:grid-cols-[9rem_1fr]"><time className="text-xs text-[hsl(var(--muted-foreground))]">{formatDateTime(item.occurredAt)}</time><div><p className="text-sm font-medium">{item.summary}</p><p className="mt-1 text-[11px] font-semibold uppercase tracking-[.08em] text-[hsl(var(--muted-foreground))]">{item.action.replaceAll("_", " ")}</p></div></li>)}</ol>}
    </section>
  </>;
}

function ClientEditor({client,onDone,onError}:{client:Client;onDone:()=>void;onError:(value:string|null)=>void}){
  const { authorizedRequest } = useAuth(); const [name,setName]=useState(client.name); const [status,setStatus]=useState<ClientStatus>(client.status); const [busy,setBusy]=useState(false);
  async function save(event:React.FormEvent){event.preventDefault();setBusy(true);onError(null);try{await authorizedRequest(`/clients/${client.id}`,{method:"PATCH",body:JSON.stringify({name,status,version:client.version})});onDone();}catch(error){onError(message(error,"Client could not be updated."));}finally{setBusy(false)}}
  return <form onSubmit={save} className="space-y-4 p-5"><div className="space-y-2"><Label htmlFor="client-edit-name">Company name</Label><Input id="client-edit-name" value={name} onChange={(event)=>setName(event.target.value)} required maxLength={180}/></div><div className="space-y-2"><Label htmlFor="client-status">Relationship status</Label><Select id="client-status" value={status} onChange={(event)=>setStatus(event.target.value as ClientStatus)}><option value="PROSPECT">Prospect</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></Select></div><div className="flex justify-end"><Button disabled={busy || !name.trim()}>{busy?<Loader2 className="size-4 animate-spin"/>:<Save className="size-4"/>}Save client</Button></div></form>
}

function ContactEditor({clientId,contact,onDone,onCancel}:{clientId:string;contact?:ClientContact;onDone:()=>void;onCancel:()=>void}){
  const { authorizedRequest }=useAuth();const [displayName,setDisplayName]=useState(contact?.displayName??"");const [email,setEmail]=useState(contact?.email??"");const [jobTitle,setJobTitle]=useState(contact?.jobTitle??"");const [phone,setPhone]=useState(contact?.phone??"");const [busy,setBusy]=useState(false);const [error,setError]=useState<string|null>(null);
  async function save(event:React.FormEvent){event.preventDefault();setBusy(true);setError(null);try{const payload={displayName,email,jobTitle:jobTitle||null,phone:phone||null,...(contact?{version:contact.version}:{})};await authorizedRequest(contact?`/client-contacts/${contact.id}`:`/clients/${clientId}/contacts`,{method:contact?"PATCH":"POST",body:JSON.stringify(payload)});onDone();}catch(requestError){setError(message(requestError,"Contact could not be saved."));}finally{setBusy(false)}}
  return <form onSubmit={save} className="border-b bg-[hsl(var(--surface-subtle))] p-5"><div className="grid gap-4 sm:grid-cols-2"><div className="space-y-2"><Label htmlFor={`contact-name-${contact?.id??"new"}`}>Full name</Label><Input id={`contact-name-${contact?.id??"new"}`} value={displayName} onChange={(event)=>setDisplayName(event.target.value)} required maxLength={160}/></div><div className="space-y-2"><Label htmlFor={`contact-email-${contact?.id??"new"}`}>Email</Label><Input id={`contact-email-${contact?.id??"new"}`} type="email" value={email} onChange={(event)=>setEmail(event.target.value)} required maxLength={320}/></div><div className="space-y-2"><Label htmlFor={`contact-title-${contact?.id??"new"}`}>Job title</Label><Input id={`contact-title-${contact?.id??"new"}`} value={jobTitle} onChange={(event)=>setJobTitle(event.target.value)} maxLength={120}/></div><div className="space-y-2"><Label htmlFor={`contact-phone-${contact?.id??"new"}`}>Phone</Label><Input id={`contact-phone-${contact?.id??"new"}`} value={phone} onChange={(event)=>setPhone(event.target.value)} maxLength={40}/></div></div>{error&&<div className="mt-4"><FormMessage>{error}</FormMessage></div>}<div className="mt-4 flex justify-end gap-2"><Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button><Button disabled={busy || !displayName.trim() || !email.trim()}>{busy&&<Loader2 className="size-4 animate-spin"/>}{contact?"Save contact":"Add contact"}</Button></div></form>
}

function Fact({label,children}:{label:string;children:React.ReactNode}){return <div className="grid grid-cols-[8rem_1fr] gap-4 px-5 py-4"><dt className="text-xs font-medium text-[hsl(var(--muted-foreground))]">{label}</dt><dd className="text-sm">{children}</dd></div>}
function formatDate(value:string){return new Intl.DateTimeFormat(undefined,{dateStyle:"medium"}).format(new Date(value))}
function formatDateTime(value:string){return new Intl.DateTimeFormat(undefined,{dateStyle:"medium",timeStyle:"short"}).format(new Date(value))}
function message(error:unknown,fallback:string){return error instanceof ApiClientError?error.message:fallback}
