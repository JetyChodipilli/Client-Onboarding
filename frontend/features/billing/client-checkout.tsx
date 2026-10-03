"use client";
import Script from "next/script";
import { useRef, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { billingApi } from "./billing-api";
import { control, decimal, errorMessage, money, paise, type InvoiceView, type PaymentHistory } from "./types";

type Result = { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string };
type RazorpayOptions = { key: string; order_id: string; amount: number; currency: string; name: string; description: string; handler: (result: Result) => void; modal: { ondismiss: () => void } };
declare global { interface Window { Razorpay?: new (options: RazorpayOptions) => { open: () => void; on: (event: string, callback: () => void) => void } } }

export function ClientCheckout({ view, history, active, reload }: { view: InvoiceView; history: PaymentHistory; active: boolean; reload: () => Promise<void> }) {
  const [amount, setAmount] = useState(decimal(view.thresholdRemainingMinor || view.balanceMinor)); const [ready, setReady] = useState(false); const [busy, setBusy] = useState(false); const [message, setMessage] = useState(""); const [error, setError] = useState(""); const key = useRef({ amount: -1, value: "" });
  const pending = history.pendingCheckout;
  const collectable = active && view.balanceMinor > 0 && !["DRAFT", "VOID", "CANCELLED"].includes(view.invoice.status) && !["MANUAL", "NO_PAYMENT_REQUIRED"].includes(view.invoice.policy);
  async function pay(e: React.FormEvent) {
    e.preventDefault(); setError(""); setMessage(""); setBusy(true);
    try {
      const minor = pending?.amountMinor ?? paise(amount); if (minor < 100 || minor > view.balanceMinor) throw new Error("Enter at least INR 1.00 and no more than the remaining balance.");
      if (key.current.amount !== minor) key.current = { amount: minor, value: crypto.randomUUID() };
      const checkout = await billingApi.checkout(view.invoice.id, minor, key.current.value);
      if (checkout.status !== "READY" || !checkout.orderId || !checkout.keyId) { setMessage(checkout.status === "SETTLED" ? "This payment was already recorded. Refreshing your invoice." : "Your payment request is awaiting confirmation. Ask your project team to reconcile it before starting another payment."); await reload(); setBusy(false); return; }
      if (!window.Razorpay) throw new Error("Razorpay could not load. Refresh the page and try again.");
      const modal = new window.Razorpay({ key: checkout.keyId, order_id: checkout.orderId, amount: checkout.amountMinor, currency: checkout.currency, name: "Client Onboarding", description: view.invoice.invoiceNumber,
        handler: async (result) => { setMessage("Awaiting payment confirmation…"); try { if (result.razorpay_order_id !== checkout.orderId) throw new Error("The checkout order did not match. Contact your project team."); await billingApi.confirm(checkout.id, result.razorpay_payment_id, result.razorpay_signature); await reload(); setMessage("Payment evidence checked. The balance above shows the confirmed status."); key.current = { amount: -1, value: "" }; } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); } },
        modal: { ondismiss: () => { setBusy(false); setMessage("Checkout closed. You can resume the same payment; your invoice has not been marked paid."); void reload().catch((e) => setError(errorMessage(e))); } },
      });
      modal.on("payment.failed", () => { setBusy(false); setError("The payment was not completed. You can resume this checkout or contact your project team."); }); modal.open();
    } catch (e) { setError(errorMessage(e)); setBusy(false); try { await reload(); } catch (refreshError) { setError(`${errorMessage(e)} Status refresh also failed: ${errorMessage(refreshError)}`); } }
  }
  return <Card><h2 className="text-xl font-bold">Payment</h2>{history.providerAvailable && collectable && <Script src="https://checkout.razorpay.com/v1/checkout.js" strategy="afterInteractive" onReady={() => setReady(true)} onError={() => setError("Razorpay could not load. Refresh the page to retry.")} />}
    {error && <Alert tone="error" className="mt-4">{error}</Alert>}{message && <Alert className="mt-4">{message}</Alert>}
    {!active ? <p className="mt-4 text-sm text-muted-foreground">Payments are paused or this requirement is locked. Contact your project team for next steps.</p> : view.balanceMinor === 0 ? <Alert tone="success" className="mt-4">Your invoice is fully paid.</Alert> : view.invoice.policy === "NO_PAYMENT_REQUIRED" ? <p className="mt-4 text-sm">No payment is required for this onboarding step.</p> : view.invoice.policy === "MANUAL" ? <p className="mt-4 text-sm">Contact your project team for payment instructions. Your team will confirm the payment after checking its reference.</p> : !history.providerAvailable ? <Alert className="mt-4">Online payment is not available yet. Contact your project team for help.</Alert> : collectable && <form className="mt-5 space-y-4" onSubmit={pay}>
      {pending && <Alert>{pending.status === "READY" ? `Resume your existing checkout for ${money(pending.amountMinor)}.` : "Your payment request is awaiting confirmation from Razorpay. Your team must reconcile it before you retry."}</Alert>}
      <label className="block text-sm font-semibold">Amount to pay (INR)<input required inputMode="decimal" className={control} value={pending ? decimal(pending.amountMinor) : amount} disabled={busy || !!pending} onChange={(e) => setAmount(e.target.value)} /></label>
      <p className="text-sm text-muted-foreground">Remaining balance: {money(view.balanceMinor)}. You may pay in parts. {view.thresholdRemainingMinor > 0 ? `${money(view.thresholdRemainingMinor)} more is required to complete this onboarding step.` : "The onboarding payment requirement is already met."}</p>
      <Button type="submit" variant="accent" disabled={busy || !ready || !!pending && pending.status !== "READY"}>{busy ? "Awaiting confirmation…" : !ready ? "Loading secure checkout…" : pending ? "Resume Razorpay checkout" : "Pay with Razorpay"}</Button><p className="text-xs leading-5 text-muted-foreground">Razorpay handles your payment details. Your invoice updates after payment is verified.</p>
    </form>}</Card>;
}
