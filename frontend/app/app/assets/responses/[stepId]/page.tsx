import { InternalAssetResponse } from "@/features/assets/asset-response";
export default async function Page({ params }: { params: Promise<{ stepId: string }> }) {
  return <InternalAssetResponse stepId={(await params).stepId} />;
}
