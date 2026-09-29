"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { portalApi } from "@/features/portal/portal-api";
import type { PortalDashboard } from "@/features/portal/types";
import { ApiClientError } from "@/lib/api-client";
import { billingApi } from "./billing-api";
import { BillingAdmin, RefundPanel } from "./billing-admin";
import { ClientCheckout } from "./client-checkout";
import { InvoiceCreate } from "./invoice-create";
import { errorMessage, money, type InvoiceView, type PaymentHistory } from "./types";

export function InternalInvoice({ invoiceId, stepId }: { invoiceId?: string; stepId?: string }) {
  const user = useCurrentUser();
  if (!user.permissions.includes("INVOICE_READ")) return <Alert>You do not have permission to read invoices.</Alert>;
  return <InvoiceDetail invoiceId={invoiceId} stepId={stepId} permissions={user.permissions} />;
}

export function InvoiceDetail({ invoiceId, stepId, projectId, permissions = [] }: { invoiceId?: string; stepId?: string; projectId?: string; permissions?: string[] }) {
  const [view, setView] = useState<InvoiceView>(); const [history, setHistory] = useState<PaymentHistory>(); const [dashboard, setDashboard] = useState<PortalDashboard>();
  const [error, setError] = useState(""); const [missing, setMissing] = useState(false); const [busy, setBusy] = useState(false); const [page, setPage] = useState(0); const errorRef = useRef<HTMLDivElement>(null);
  const load = useCallback(async () => {
    const dashboard = projectId ? await portalApi.dashboard(projectId) : undefined;
    let value: InvoiceView;
    try { value = invoiceId ? await billingApi.get(invoiceId) : await billingApi.step(stepId!, projectId); }
    catch (e) { if (e instanceof ApiClientError && e.status === 404 && stepId) { return { dashboard, missing: true, view: undefined, history: undefined }; } throw e; }
    const payments = await billingApi.history(value.invoice.id, !!projectId, page);
    return { dashboard, missing: false, view: value, history: payments };
  }, [invoiceId, stepId, projectId, page]);
  const receive = useCallback((result: Awaited<ReturnType<typeof load>>) => { setDashboard(result.dashboard); setView(result.view); setHistory(result.history); setMissing(result.missing); }, []);
  useEffect(() => { let active = true; load().then((result) => { if (active) receive(result); }).catch((e) => { if (active) setError(errorMessage(e)); }); return () => { active = false; }; }, [load, receive]);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  async function reload() { setBusy(true); setError(""); try { receive(await load()); } catch (e) { setError(errorMessage(e)); throw e; } finally { setBusy(false); } }
  const current = dashboard?.steps.find((s) => s.id === stepId);
  const active = dashboard?.projectStatus === "ONBOARDING" && dashboard.onboardingStatus === "IN_PROGRESS" && !!current && ["AVAILABLE", "IN_PROGRESS", "NEEDS_REVISION", "COMPLETED"].includes(current.status);
  return <>
    <Link className="inline-flex min-h-11 items-center font-semibold text-primary hover:underline" href={projectId ? `/portal/projects/${projectId}` : view ? `/app/projects/${view.invoice.projectId}` : "/app/invoices"}>← {projectId || view ? "Back to project" : "All invoices"}</Link>
    {error && <div ref={errorRef} tabIndex={-1} className="mt-5"><Alert tone="error">{error}<Button className="mt-3" variant="outline" disabled={busy} onClick={() => { void reload().catch(() => undefined); }}>Retry invoice</Button></Alert></div>}
    {!view && !missing && !error && <p className="mt-6" aria-busy="true">Loading invoice…</p>}
    {dashboard && <section aria-label="Payment step context" className="mt-6 grid gap-4 md:grid-cols-2"><Card><h2 className="font-bold">Your action</h2><p className="mt-2 text-sm">{missing ? "Wait for your project team to publish the invoice." : active && view && !view.requirementSatisfied ? "Review the invoice and pay the amount required for this onboarding step." : "Review your payment status below. No payment is needed for this step right now."}</p><p className="mt-3 text-sm"><strong>Current status:</strong> {current?.status.replaceAll("_", " ") || dashboard.currentStatus.replaceAll("_", " ")}</p><p className="mt-2 text-sm"><strong>Project progress:</strong> {dashboard.progress}%</p><p className="mt-2 text-sm"><strong>Deadline:</strong> {view?.invoice.dueDate || (current?.deadline ? new Date(current.deadline).toLocaleDateString() : "Not set")}</p></Card><Card><h2 className="font-bold">Waiting for our team</h2><p className="mt-2 text-sm">{missing ? "Your team is preparing the invoice." : !active ? "Your team must resume the project or complete its prerequisites." : history?.pendingCheckout && history.pendingCheckout.status !== "READY" ? "Your team must reconcile the pending payment request." : "Your team can help with payment questions or missing confirmations."}</p><p className="mt-3 text-sm"><strong>Blocking reason:</strong> {dashboard.blockingReason || current?.blockingReason || (view?.thresholdRemainingMinor ? `${money(view.thresholdRemainingMinor)} remains before this step is complete.` : "None")}</p><p className="mt-2 text-sm"><strong>Available help:</strong> {dashboard.availableHelp}</p></Card></section>}
    {missing && !projectId && stepId && (permissions.includes("INVOICE_CREATE") ? <InvoiceCreate stepId={stepId} created={(r) => { setView(r); setMissing(false); void reload().catch(() => undefined); }} /> : <Alert className="mt-6">Your team has not created an invoice for this step yet.</Alert>)}
    {view && history && <>
      <div className="mt-6 flex flex-wrap items-start justify-between gap-4"><div className="min-w-0 flex-1"><h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Invoice</h1><p className="mt-2 text-sm text-muted-foreground [overflow-wrap:anywhere]">{view.invoice.invoiceNumber}</p></div><Badge tone={view.requirementSatisfied ? "success" : view.displayStatus === "OVERDUE" ? "warning" : "info"}>{view.displayStatus.replaceAll("_", " ")}</Badge></div>
      <section className="mt-6 grid gap-4 md:grid-cols-3" aria-label="Invoice balance"><Card><p className="text-sm text-muted-foreground">Remaining balance</p><p className="mt-2 font-mono text-2xl font-bold tabular-nums [overflow-wrap:anywhere]">{money(view.balanceMinor)}</p><p className="mt-2 text-xs">Due {view.invoice.dueDate}</p></Card><Card><p className="text-sm text-muted-foreground">Confirmed paid</p><p className="mt-2 font-mono text-2xl font-bold tabular-nums [overflow-wrap:anywhere]">{money(view.paidMinor)}</p><p className="mt-2 text-xs">After {money(view.invoice.refundedMinor)} refunded</p></Card><Card><p className="text-sm text-muted-foreground">Required for this step</p><p className="mt-2 font-mono text-2xl font-bold tabular-nums [overflow-wrap:anywhere]">{money(view.thresholdRemainingMinor)}</p><p className="mt-2 text-xs">{view.requirementSatisfied ? "Payment requirement met" : `${view.invoice.policy.replaceAll("_", " ")} · threshold ${money(view.invoice.thresholdMinor)}`}</p></Card></section>
      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]"><Card><h2 className="text-xl font-bold">Invoice items</h2><ul className="mt-4 divide-y">{view.items.map((item, index) => <li key={index} className="flex flex-wrap justify-between gap-3 py-4"><div className="min-w-0 flex-1"><p className="font-semibold [overflow-wrap:anywhere]">{item.description}</p><p className="mt-1 text-sm text-muted-foreground">{item.quantity} × {money(item.unitAmountMinor)} · {item.taxBasisPoints / 100}% tax</p></div><p className="font-mono tabular-nums">{money(item.totalMinor || 0)}</p></li>)}</ul><dl className="space-y-3 border-t pt-4"><div className="flex justify-between gap-4"><dt>Subtotal</dt><dd className="font-mono">{money(view.invoice.subtotalMinor)}</dd></div><div className="flex justify-between gap-4"><dt>Tax</dt><dd className="font-mono">{money(view.invoice.taxMinor)}</dd></div><div className="flex justify-between gap-4 text-lg font-bold"><dt>Total</dt><dd className="font-mono">{money(view.invoice.totalMinor)}</dd></div></dl>{view.invoice.note && <p className="mt-5 whitespace-pre-wrap border-t pt-4 text-sm [overflow-wrap:anywhere]">{view.invoice.note}</p>}</Card>
        <div>{projectId ? <ClientCheckout key={view.invoice.id} view={view} history={history} active={active} reload={reload} /> : <BillingAdmin view={view} history={history} permissions={permissions} reload={reload} />}</div></div>
      <section className="mt-9" aria-labelledby="payment-history"><h2 id="payment-history" className="text-xl font-bold">Payment history</h2>{!history.transactions.items.length ? <p className="mt-4 text-sm text-muted-foreground">No payment transactions recorded yet.</p> : <ul className="mt-4 space-y-3">{history.transactions.items.map((t) => <li key={t.id}><Card><div className="flex flex-wrap justify-between gap-4"><div className="min-w-0"><p className="font-semibold">{money(t.amountMinor)} · {t.provider}</p><p className="mt-2 text-xs text-muted-foreground">{new Date(t.createdAt).toLocaleString()}</p><p className="mt-2 text-xs [overflow-wrap:anywhere]">{t.providerPaymentId || t.reference}</p></div><Badge>{t.status.replaceAll("_", " ")}</Badge></div>{t.refundedMinor > 0 && <p className="mt-3 text-sm">Refunded: {money(t.refundedMinor)}</p>}{!projectId && permissions.includes("PAYMENT_REFUND") && <details className="mt-4"><summary className="min-h-11 cursor-pointer font-semibold text-primary">Review or request refund</summary><RefundPanel transaction={t} reload={reload} /></details>}</Card></li>)}</ul>}<div className="mt-4 flex flex-wrap gap-3"><Button variant="outline" disabled={page === 0 || busy} onClick={() => setPage(page - 1)}>Newer payments</Button><Button variant="outline" disabled={(page + 1) * 20 >= history.transactions.totalElements || busy} onClick={() => setPage(page + 1)}>Older payments</Button><Button variant="ghost" disabled={busy} onClick={() => { void reload().catch(() => undefined); }}>Refresh payment status</Button></div></section>
    </>}
  </>;
}
