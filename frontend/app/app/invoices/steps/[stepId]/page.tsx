import { InternalInvoice } from "@/features/billing/invoice-detail";
export default async function Page({ params }: { params: Promise<{ stepId: string }> }) { const { stepId } = await params; return <InternalInvoice stepId={stepId} />; }
