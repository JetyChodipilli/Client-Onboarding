import { InternalFormResponse } from "@/features/forms/form-response";
export default async function Page({ params }: { params: Promise<{ stepId: string }> }) { return <InternalFormResponse stepId={(await params).stepId} />; }
