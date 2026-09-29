import { AssetResponsePanel } from "@/features/assets/asset-response";
export default async function Page({ params }: { params: Promise<{ projectId: string; stepId: string }> }) {
  const { projectId, stepId } = await params;
  return <AssetResponsePanel projectId={projectId} stepId={stepId} />;
}
