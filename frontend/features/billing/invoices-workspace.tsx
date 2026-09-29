"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { billingApi } from "./billing-api";
import { control, errorMessage, money, type InvoiceView } from "./types";

export function InvoicesWorkspace() {
  const user = useCurrentUser(); const [rows, setRows] = useState<InvoiceView[]>(); const [search, setSearch] = useState(""); const [status, setStatus] = useState(""); const [page, setPage] = useState(0); const [error, setError] = useState(""); const [refresh, setRefresh] = useState(0);
  const allowed = user.permissions.includes("INVOICE_READ");
  useEffect(() => { if (!allowed) return; let active = true; const timer = setTimeout(() => billingApi.list(search, status, page).then((r) => { if (active) { setRows(r); setError(""); } }).catch((e) => { if (active) setError(errorMessage(e)); }), 250); return () => { active = false; clearTimeout(timer); }; }, [allowed, search, status, page, refresh]);
  if (!allowed) return <Alert>You do not have permission to read invoices.</Alert>;
  return <><div className="flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Invoices</h1><p className="mt-3 text-muted-foreground">Track balances, confirm payments and keep every project moving.</p></div>{user.permissions.includes("INVOICE_CREATE") && <Link className="inline-flex min-h-11 items-center rounded-md bg-accent px-4 font-semibold text-accent-foreground" href="/app/projects">Create from a project</Link>}</div>
    <div className="mt-7 grid gap-4 sm:grid-cols-2"><label className="text-sm font-semibold">Search invoice number<input className={control} value={search} maxLength={100} onChange={(e) => { setSearch(e.target.value); setPage(0); }} /></label><label className="text-sm font-semibold">Invoice status<select className={control} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">All statuses</option>{["DRAFT", "SENT", "VIEWED", "PARTIALLY_PAID", "PAID", "OVERDUE", "REFUNDED", "PARTIALLY_REFUNDED", "VOID", "CANCELLED"].map((s) => <option key={s} value={s}>{s.replaceAll("_", " ")}</option>)}</select></label></div>
    {error && <Alert tone="error" className="mt-5">{error}<Button variant="outline" className="mt-3" onClick={() => setRefresh(refresh + 1)}>Retry invoices</Button></Alert>}
    {!rows && !error ? <p className="mt-8" aria-busy="true">Loading invoices…</p> : rows?.length === 0 ? <Card className="mt-8"><h2 className="font-bold">No invoices found</h2><p className="mt-2 text-sm text-muted-foreground">Open a project with a payment step to create its first invoice, or change your filters.</p></Card> : <ul className="mt-7 space-y-3">{rows?.map((r) => <li key={r.invoice.id}><Card className="flex flex-wrap items-center justify-between gap-4"><div className="min-w-0 flex-1"><Link className="inline-flex min-h-11 items-center font-semibold text-primary underline [overflow-wrap:anywhere]" href={`/app/invoices/${r.invoice.id}`}>{r.invoice.invoiceNumber}</Link><p className="mt-1 text-sm text-muted-foreground">Due {r.invoice.dueDate} · {r.invoice.policy.replaceAll("_", " ")}</p></div><div className="text-right"><Badge tone={r.requirementSatisfied ? "success" : r.displayStatus === "OVERDUE" ? "warning" : "neutral"}>{r.displayStatus.replaceAll("_", " ")}</Badge><p className="mt-2 font-mono text-lg font-bold tabular-nums">{money(r.balanceMinor)}</p><p className="text-xs text-muted-foreground">remaining of {money(r.invoice.totalMinor)}</p></div></Card></li>)}</ul>}
    <div className="mt-5 flex gap-3"><Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous invoices</Button><Button variant="outline" disabled={!rows || rows.length < 20} onClick={() => setPage(page + 1)}>Next invoices</Button></div></>;
}
