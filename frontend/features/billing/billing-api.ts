import { apiRequest } from "@/lib/api-client";
import type { Checkout, InvoiceItem, InvoiceStatus, InvoiceView, PaymentHistory, Refund } from "./types";
const post = (data: unknown, key?: string): RequestInit => ({ method: "POST", headers: { "Content-Type": "application/json", ...(key ? { "Idempotency-Key": key } : {}) }, body: JSON.stringify(data) });
export const billingApi = {
  list: (search: string, status: string, page: number, project?: string) => apiRequest<InvoiceView[]>(`/api/v1/invoices?search=${encodeURIComponent(search)}&page=${page}&size=20${status ? `&status=${status}` : ""}${project ? `&projectId=${project}` : ""}`),
  get: (id: string) => apiRequest<InvoiceView>(`/api/v1/invoices/${id}`),
  step: (step: string, project?: string) => apiRequest<InvoiceView | null>(project ? `/api/v1/client-portal/projects/${project}/payments/${step}` : `/api/v1/invoices/by-step/${step}`),
  create: (stepId: string, dueDate: string, note: string, items: InvoiceItem[], key: string) => apiRequest<InvoiceView>("/api/v1/invoices", post({ stepId, dueDate, note, items }, key)),
  publish: (id: string, version: number) => apiRequest<InvoiceView>(`/api/v1/invoices/${id}/send`, post({ version })),
  close: (id: string, version: number, status: InvoiceStatus, reason: string) => apiRequest<InvoiceView>(`/api/v1/invoices/${id}/close`, post({ version, status, reason })),
  viewed: (id: string) => apiRequest<InvoiceView>(`/api/v1/client-portal/invoices/${id}/viewed`, post({})),
  history: (id: string, client: boolean, page = 0) => apiRequest<PaymentHistory>(`/api/v1/${client ? "client-portal/" : ""}invoices/${id}/payments?page=${page}&size=20`),
  checkout: (id: string, amountMinor: number, key: string) => apiRequest<Checkout>(`/api/v1/client-portal/invoices/${id}/checkout`, post({ amountMinor }, key)),
  confirm: (id: string, paymentId: string, signature: string) => apiRequest(`/api/v1/client-portal/payments/${id}/confirm`, post({ paymentId, signature })),
  reconcile: (id: string, providerId?: string) => apiRequest(`/api/v1/payments/${id}/reconcile`, post({ providerId })),
  sync: (id: string) => apiRequest(`/api/v1/invoices/${id}/sync-workflow`, post({})),
  manual: (id: string, amountMinor: number, reference: string, reason: string, key: string) => apiRequest(`/api/v1/invoices/${id}/manual-payments`, post({ amountMinor, reference, reason }, key)),
  refunds: (id: string, page = 0) => apiRequest<Refund[]>(`/api/v1/payment-transactions/${id}/refunds?page=${page}&size=20`),
  refund: (id: string, amountMinor: number, reason: string, key: string) => apiRequest<Refund>(`/api/v1/payment-transactions/${id}/refunds`, post({ amountMinor, reason }, key)),
  reconcileRefund: (id: string, providerId?: string) => apiRequest<Refund>(`/api/v1/refunds/${id}/reconcile`, post({ providerId })),
};
