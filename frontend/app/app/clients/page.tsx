"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowRight, Building2, Loader2, Plus, RefreshCw, Search, UsersRound } from "lucide-react";
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
import { ApiClientError } from "@/services/api-client";
import type { PageMeta } from "@/types/api";
import type { Client, ClientStatus } from "@/types/client";

const PAGE_SIZE = 25;
const EMPTY_META: PageMeta = { page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

export default function ClientsPage() {
  const { authorizedRequest, authorizedRequestWithMeta, hasPermission } = useAuth();
  const canRead = hasPermission("CLIENT_READ");
  const canCreate = hasPermission("CLIENT_CREATE");
  const [clients, setClients] = useState<Client[]>([]);
  const [meta, setMeta] = useState<PageMeta>(EMPTY_META);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [query, setQuery] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [status, setStatus] = useState<ClientStatus | "ALL">("ALL");
  const [error, setError] = useState<string | null>(null);
  const loadSequence = useRef(0);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      setPage(0);
      setSearchQuery(query.trim());
    }, 300);
    return () => window.clearTimeout(handle);
  }, [query]);

  const load = useCallback(async () => {
    const sequence = ++loadSequence.current;
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (status !== "ALL") params.set("status", status);
      if (searchQuery) params.set("query", searchQuery);
      const result = await authorizedRequestWithMeta<Client[], PageMeta>(`/clients?${params}`);
      if (sequence !== loadSequence.current) return;
      if (result.meta.totalPages > 0 && page >= result.meta.totalPages) {
        setPage(result.meta.totalPages - 1);
        return;
      }
      setClients(result.data);
      setMeta(result.meta);
    } catch (requestError) {
      if (sequence !== loadSequence.current) return;
      setError(requestError instanceof ApiClientError ? requestError.message : "Clients could not be loaded.");
    } finally {
      if (sequence === loadSequence.current) setLoading(false);
    }
  }, [authorizedRequestWithMeta, page, searchQuery, status]);

  useEffect(() => { if (canRead) void load(); }, [canRead, load]);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    setCreating(true);
    setError(null);
    try {
      await authorizedRequest<Client>("/clients", { method: "POST", body: JSON.stringify({ name }) });
      setName("");
      setShowCreate(false);
      if (page !== 0) setPage(0); else await load();
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "Client could not be created.");
    } finally {
      setCreating(false);
    }
  }

  if (!canRead) return <PermissionState />;

  return <>
    <PageHeading
      eyebrow="Relationships"
      title="Clients"
      description="Keep each client company separate from its people and projects. Archived records remain traceable instead of being physically deleted."
      actions={<div className="flex gap-2"><Button variant="secondary" size="icon" onClick={() => void load()} aria-label="Refresh clients"><RefreshCw className="size-4"/></Button>{canCreate && <Button onClick={() => setShowCreate((value) => !value)}><Plus className="size-4"/>New client</Button>}</div>}
    />

    {showCreate && <section className="panel mb-6 p-5 sm:p-6"><div className="grid gap-5 lg:grid-cols-[1fr_.75fr]"><div><p className="text-xs font-bold uppercase tracking-[.14em] text-[hsl(var(--primary))]">New relationship</p><h2 className="mt-2 text-lg font-semibold">Create the client company first</h2><p className="mt-2 max-w-xl text-sm leading-6 text-[hsl(var(--muted-foreground))]">Contacts are added after creation so people remain distinct from the external company record.</p></div><form onSubmit={create} className="space-y-3"><div className="space-y-2"><Label htmlFor="client-name">Company name</Label><Input id="client-name" value={name} onChange={(event) => setName(event.target.value)} placeholder="Acme Technologies" required maxLength={180}/></div><div className="flex justify-end gap-2"><Button type="button" variant="ghost" onClick={() => setShowCreate(false)}>Cancel</Button><Button disabled={creating || !name.trim()}>{creating && <Loader2 className="size-4 animate-spin"/>}Create client</Button></div></form></div></section>}

    {error && <div className="mb-5"><FormMessage>{error}</FormMessage></div>}

    <section className="panel overflow-hidden">
      <div className="grid gap-3 border-b p-4 sm:grid-cols-[1fr_13rem] sm:p-5">
        <label className="relative"><span className="sr-only">Search clients</span><Search className="pointer-events-none absolute left-3.5 top-3.5 size-4 text-[hsl(var(--muted-foreground))]"/><Input className="pl-10" value={query} maxLength={120} onChange={(event) => setQuery(event.target.value)} placeholder="Search client name…"/></label>
        <Select value={status} onChange={(event) => { setStatus(event.target.value as ClientStatus | "ALL"); setPage(0); }} aria-label="Filter client status"><option value="ALL">All active records</option><option value="PROSPECT">Prospect</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></Select>
      </div>
      {loading ? <Loading label="Loading client relationships…"/> : clients.length === 0 ? <EmptyState title={searchQuery ? "No matching clients" : "No clients yet"} description={searchQuery ? "Try a broader company-name prefix or change the status filter." : "Create the first client company. Contacts and projects can then be attached without collapsing those concepts together."}/> : <div className="overflow-x-auto"><table className="data-table"><thead><tr><th>Client</th><th>Status</th><th>Updated</th><th className="w-36">Open</th></tr></thead><tbody>{clients.map((client) => <tr key={client.id}><td><div className="flex items-center gap-3"><div className="grid size-10 place-items-center rounded-xl bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]"><Building2 className="size-4.5"/></div><div><p className="font-semibold">{client.name}</p><p className="mt-0.5 flex items-center gap-1.5 text-xs text-[hsl(var(--muted-foreground))]"><UsersRound className="size-3.5"/>Company record</p></div></div></td><td><StatusBadge status={client.status}/></td><td className="text-[hsl(var(--muted-foreground))]">{formatDate(client.updatedAt)}</td><td><Button asChild variant="ghost" size="sm"><Link href={`/app/clients/${client.id}`}>View client<ArrowRight className="size-4"/></Link></Button></td></tr>)}</tbody></table></div>}
      <PaginationControls meta={meta} disabled={loading} onPageChange={setPage}/>
    </section>
  </>;
}

function Loading({label}:{label:string}){return <div className="flex items-center justify-center py-16 text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin"/>{label}</div>}
function formatDate(value:string){return new Intl.DateTimeFormat(undefined,{dateStyle:"medium"}).format(new Date(value))}
