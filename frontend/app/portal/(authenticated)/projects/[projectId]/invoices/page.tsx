"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, Loader2, ReceiptText } from "lucide-react";
import { useClientAuth } from "@/auth/client-auth-provider";
import { EmptyState } from "@/components/shared/empty-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/services/api-client";
import type { InvoiceSummary } from "@/types/billing";

export default function ClientInvoicesPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const { authorizedRequest } = useClientAuth();
  const [items, setItems] = useState<InvoiceSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setItems(await authorizedRequest<InvoiceSummary[]>(`/client-portal/projects/${projectId}/invoices?size=50`)); }
    catch (e) { setError(e instanceof ApiClientError ? e.message : "Invoices could not be loaded."); }
    finally { setLoading(false); }
  }, [authorizedRequest, projectId]);
  useEffect(() => { void load(); }, [load]);

  return <div className="mx-auto max-w-5xl">
    <Button asChild variant="ghost" size="sm" className="mb-5 -ml-2"><Link href={`/portal/projects/${projectId}`}><ArrowLeft className="size-4"/>Back to project</Link></Button>
    <header className="mb-7"><div className="flex items-center gap-2 text-xs font-bold uppercase tracking-[.14em] text-[hsl(var(--primary))]"><ReceiptText className="size-4"/>Payments</div><h1 className="mt-3 text-3xl font-semibold tracking-[-.045em]">Invoices and payment status</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-[hsl(var(--muted-foreground))]">Review amounts, payment progress and any action currently required from you. Provider-confirmed payment status remains authoritative.</p></header>
    {loading ? <div className="panel grid min-h-56 place-items-center text-sm text-[hsl(var(--muted-foreground))]"><span className="flex items-center gap-2"><Loader2 className="size-4 animate-spin"/>Loading invoices…</span></div> : error ? <div className="panel p-6"><p className="font-semibold">Invoices unavailable</p><p className="mt-2 text-sm text-[hsl(var(--muted-foreground))]">{error}</p><Button className="mt-4" variant="secondary" onClick={() => void load()}>Try again</Button></div> : items.length === 0 ? <EmptyState title="No invoices yet" description="When an invoice is sent for this project, it will appear here."/> : <div className="space-y-3">{items.map(invoice => <Link key={invoice.id} href={`/portal/projects/${projectId}/invoices/${invoice.id}`} className="panel flex items-center gap-4 p-5 transition-colors hover:bg-[hsl(var(--surface-subtle)/.55)]"><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><p className="font-semibold">{invoice.invoiceNumber}</p><StatusBadge status={invoice.status}/></div><p className="mt-2 text-sm text-[hsl(var(--muted-foreground))]">{money(invoice.totalMinor, invoice.currency)} total · {money(invoice.balanceDueMinor, invoice.currency)} remaining</p>{invoice.dueAt && <p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Due {date(invoice.dueAt)}</p>}</div><ArrowRight className="size-4 shrink-0 text-[hsl(var(--muted-foreground))]"/></Link>)}</div>}
  </div>;
}

function money(value:number,currency:string){try{return new Intl.NumberFormat(undefined,{style:"currency",currency}).format(value/100)}catch{return `${currency} ${(value/100).toFixed(2)}`}}
function date(value:string){return new Intl.DateTimeFormat(undefined,{dateStyle:"medium"}).format(new Date(value))}
