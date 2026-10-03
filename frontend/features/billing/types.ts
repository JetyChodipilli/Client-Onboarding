export const control = "mt-2 min-h-11 w-full rounded-md border bg-background px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-60";
export type InvoiceStatus = "DRAFT" | "SENT" | "VIEWED" | "PARTIALLY_PAID" | "PAID" | "OVERDUE" | "VOID" | "CANCELLED" | "REFUNDED" | "PARTIALLY_REFUNDED";
export type InvoiceItem = { description: string; quantity: number; unitAmountMinor: number; taxBasisPoints: number; totalMinor?: number; taxMinor?: number; subtotalMinor?: number };
export type InvoiceView = {
  invoice: { id: string; projectId: string; stepId: string; invoiceNumber: string; currency: string; policy: string; subtotalMinor: number; taxMinor: number; totalMinor: number; thresholdMinor: number; capturedMinor: number; refundedMinor: number; reservedMinor: number; status: InvoiceStatus; dueDate: string; note?: string; createdAt: string; version: number };
  items: InvoiceItem[]; displayStatus: InvoiceStatus; paidMinor: number; balanceMinor: number; thresholdRemainingMinor: number; requirementSatisfied: boolean;
};
export type Checkout = { id: string; invoiceId: string; status: "CREATING" | "READY" | "UNKNOWN" | "SETTLED" | "FAILED"; keyId?: string; orderId?: string; amountMinor: number; currency: string; receipt: string; version: number };
export type PaymentTransaction = { id: string; paymentId?: string; provider: string; providerPaymentId?: string; status: string; amountMinor: number; refundedMinor: number; reference?: string; reason?: string; createdAt: string };
export type PaymentHistory = { transactions: { items: PaymentTransaction[]; page: number; size: number; totalElements: number }; pendingCheckout?: Checkout; providerAvailable: boolean };
export type Refund = { id: string; transactionId: string; providerRefundId?: string; amountMinor: number; status: string; reason: string; createdAt: string };
export const money = (minor: number) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(minor / 100);
export const decimal = (minor: number) => `${Math.floor(minor / 100)}.${String(minor % 100).padStart(2, "0")}`;
export function paise(value: string): number {
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(value.trim())) throw new Error("Enter an INR amount with no more than two decimal places.");
  const [whole, fraction = ""] = value.trim().split(".");
  const amount = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(amount) || amount > 100_000_000_000) throw new Error("The amount exceeds the supported limit.");
  return amount;
}
export function itemPreview(items: InvoiceItem[]) {
  return items.reduce((sum, item) => { const base = item.quantity * item.unitAmountMinor; return sum + base + Math.floor((base * item.taxBasisPoints + 5000) / 10000); }, 0);
}
export const errorMessage = (e: unknown) => e instanceof Error ? e.message : "This action could not be completed. Refresh and try again.";
