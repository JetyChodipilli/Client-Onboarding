import { InternalInvoice } from "@/features/billing/invoice-detail";
export default async function Page({ params }: { params: Promise<{ invoiceId: string }> }) { const { invoiceId } = await params; return <InternalInvoice invoiceId={invoiceId} />; }
