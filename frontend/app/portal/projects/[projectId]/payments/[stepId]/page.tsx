import { InvoiceDetail } from "@/features/billing/invoice-detail";
export default async function Page({ params }: { params: Promise<{ projectId: string; stepId: string }> }) { const { projectId, stepId } = await params; return <InvoiceDetail projectId={projectId} stepId={stepId} />; }
