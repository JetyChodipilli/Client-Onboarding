import { FormResponsePanel } from "@/features/forms/form-response";
export default async function Page({ params }: { params: Promise<{ projectId: string; stepId: string }> }) {
  const { projectId, stepId } = await params;
  return <FormResponsePanel projectId={projectId} stepId={stepId} />;
}
