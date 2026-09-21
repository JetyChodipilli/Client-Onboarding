"use client";

import { ArrowLeft, Plus } from "lucide-react";
import Link from "next/link";
import { type FormEvent, useEffect, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { Failure, PendingRows, errorMessage } from "./operations-pages";
import { operationsApi } from "./operations-api";
import type { Client, ClientContact } from "./types";

export function ClientDetail({ clientId }: { clientId: string }) {
  const user = useCurrentUser();
  const [client, setClient] = useState<Client>();
  const [contacts, setContacts] = useState<ClientContact[]>();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [primary, setPrimary] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const canUpdate = user.permissions.includes("CLIENT_UPDATE");
  const load = () => { setError(""); Promise.all([operationsApi.client(clientId), operationsApi.contacts(clientId)]).then(([clientData, contactData]) => { setClient(clientData); setContacts(contactData); }).catch((cause) => setError(errorMessage(cause))); };
  useEffect(() => { Promise.all([operationsApi.client(clientId), operationsApi.contacts(clientId)]).then(([clientData, contactData]) => { setClient(clientData); setContacts(contactData); }).catch((cause) => setError(errorMessage(cause))); }, [clientId]);
  async function add(event: FormEvent) { event.preventDefault(); setPending(true); setError(""); try { await operationsApi.createContact(clientId, { name, email, primary }); setName(""); setEmail(""); setPrimary(false); load(); } catch (cause) { setError(errorMessage(cause)); } finally { setPending(false); } }
  if (error && !client) return <><Link href="/app/clients" className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-primary"><ArrowLeft aria-hidden="true" className="size-4" />Clients</Link><Failure message={error} retry={load} /></>;
  if (!client || !contacts) return <PendingRows label="Loading client" />;
  return <><Link href="/app/clients" className="inline-flex min-h-11 items-center gap-2 rounded-md text-sm font-semibold text-primary hover:underline"><ArrowLeft aria-hidden="true" className="size-4" />Clients</Link><div className="mt-4 flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm font-bold uppercase tracking-[0.15em] text-primary">Client record</p><h1 className="mt-3 text-3xl font-bold tracking-[-0.035em] sm:text-4xl">{client.name}</h1><p className="mt-3 text-muted-foreground">{client.legalName || "Contacts remain separate from client login identities."}</p></div><Badge tone={client.status === "ACTIVE" ? "success" : "neutral"}>{client.status}</Badge></div>{error && <Alert tone="error" className="mt-6">{error}</Alert>}
    {canUpdate && <Card className="mt-8"><h2 className="text-lg font-bold">Add contact</h2><form className="mt-5 grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] md:items-end" onSubmit={add}><FormField label="Contact name" htmlFor="contact-name"><Input id="contact-name" required maxLength={160} value={name} onChange={(event) => setName(event.target.value)} /></FormField><FormField label="Email" htmlFor="contact-email"><Input id="contact-email" type="email" required maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} /></FormField><div className="flex flex-col gap-2"><label className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-sm"><input type="checkbox" className="size-4 accent-primary" checked={primary} onChange={(event) => setPrimary(event.target.checked)} />Primary contact</label><Button type="submit" disabled={pending}><Plus aria-hidden="true" />{pending ? "Adding…" : "Add contact"}</Button></div></form></Card>}
    <section className="mt-8" aria-labelledby="contact-list"><div className="flex items-center justify-between gap-4"><h2 id="contact-list" className="text-xl font-bold">Contacts</h2><Badge>{contacts.filter((contact) => !contact.archivedAt).length} active</Badge></div>{contacts.length === 0 ? <Card className="mt-4"><p className="text-sm text-muted-foreground">No contacts yet.</p></Card> : <div className="mt-4 grid gap-4 md:grid-cols-2">{contacts.map((contact) => <Card key={contact.id} className={contact.archivedAt ? "opacity-60" : ""}><div className="flex items-start justify-between gap-3"><div className="min-w-0"><h3 className="[overflow-wrap:anywhere] font-bold">{contact.name}</h3><p className="mt-1 [overflow-wrap:anywhere] text-sm text-muted-foreground">{contact.email}</p><p className="mt-1 text-sm text-muted-foreground">{contact.jobTitle || "No title"}</p></div>{contact.primary && <Badge tone="info">Primary</Badge>}</div></Card>)}</div>}</section>
  </>;
}
