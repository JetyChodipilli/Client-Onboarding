export type PaymentPolicy = "FULL" | "DEPOSIT" | "MILESTONE" | "MANUAL" | "NO_PAYMENT_REQUIRED";
export type InvoiceStatus = "DRAFT" | "SENT" | "VIEWED" | "PARTIALLY_PAID" | "PAID" | "OVERDUE" | "VOID" | "CANCELLED" | "REFUNDED" | "PARTIALLY_REFUNDED";
export type PaymentStatus = "INITIATED" | "PENDING" | "AUTHORIZED" | "CAPTURED" | "FAILED" | "CANCELLED" | "REFUNDED" | "PARTIALLY_REFUNDED";
export type RefundStatus = "PENDING" | "COMPLETED" | "FAILED" | "CANCELLED";
export type PaymentTransactionType = "INITIATED" | "PENDING" | "AUTHORIZED" | "CAPTURE" | "FAILURE" | "CANCELLATION" | "REFUND" | "MANUAL_CAPTURE" | "MANUAL_REFUND";

export interface InvoiceItem { id:string;description:string;quantity:number;unitAmountMinor:number;lineTotalMinor:number;displayOrder:number }
export interface InvoiceSummary { id:string;projectId:string;invoiceNumber:string;paymentPolicy:PaymentPolicy;currency:string;status:InvoiceStatus;totalMinor:number;requiredAmountMinor:number;amountPaidMinor:number;amountRefundedMinor:number;balanceDueMinor:number;dueAt:string|null;createdAt:string;version:number }
export interface ClientPayment { id:string;requestedAmountMinor:number;capturedAmountMinor:number;refundedAmountMinor:number;currency:string;status:PaymentStatus;createdAt:string }
export interface Payment { id:string;provider:string;providerPaymentId:string|null;requestedAmountMinor:number;capturedAmountMinor:number;refundedAmountMinor:number;currency:string;status:PaymentStatus;checkoutUrl:string|null;sessionExpiresAt:string|null;failureReason:string|null;createdAt:string;version:number }
export interface PaymentTransaction { id:string;paymentId:string;type:PaymentTransactionType;amountMinor:number;currency:string;reason:string|null;occurredAt:string }
export interface Refund { id:string;paymentId:string;provider:string;providerRefundId:string|null;amountMinor:number;currency:string;status:RefundStatus;reason:string;failureReason:string|null;requestedAt:string;completedAt:string|null;version:number }
export interface ClientInvoice { id:string;projectId:string;invoiceNumber:string;paymentPolicy:PaymentPolicy;currency:string;status:InvoiceStatus;subtotalMinor:number;taxMinor:number;totalMinor:number;requiredAmountMinor:number;amountPaidMinor:number;amountRefundedMinor:number;balanceDueMinor:number;memo:string|null;dueAt:string|null;sentAt:string|null;viewedAt:string|null;paidAt:string|null;items:InvoiceItem[];payments:ClientPayment[] }
export interface Invoice extends ClientInvoice { clientId:string;onboardingId:string|null;stepId:string|null;createdAt:string;version:number;payments:Payment[];transactions:PaymentTransaction[];refunds:Refund[] }
export interface PaymentSession { paymentId:string;provider:string;checkoutUrl:string;expiresAt:string;amountMinor:number;currency:string }
