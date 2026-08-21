"use client";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import { useClientAuth } from "@/auth/client-auth-provider";
import { AssetUploadPanel } from "@/features/assets/asset-upload-panel";
import { Button } from "@/components/ui/button";

export default function ClientAssetRequirementPage(){
  const{projectId,stepId}=useParams<{projectId:string;stepId:string}>();
  const{authorizedRequest}=useClientAuth();
  const resource=`/client-portal/projects/${projectId}/assets/steps/${stepId}`;
  return <div className="mx-auto max-w-4xl">
    <Button asChild variant="ghost" size="sm" className="mb-5 -ml-2"><Link href={`/portal/projects/${projectId}`}><ArrowLeft className="size-4"/>Back to project</Link></Button>
    <header className="mb-7"><div className="flex items-center gap-2 text-xs font-bold uppercase tracking-[.14em] text-[hsl(var(--primary))]"><ShieldCheck className="size-4"/>Secure asset exchange</div><h1 className="mt-3 text-3xl font-semibold tracking-[-.045em]">Upload project assets safely</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-[hsl(var(--muted-foreground))]">Files stay private until their signature, configured type policy and malware scan pass. If review is required, your team receives the clean file only after those checks.</p></header>
    <AssetUploadPanel authorizedRequest={authorizedRequest} resourcePath={resource} downloadPath={(assetId,versionId)=>`/client-portal/projects/${projectId}/assets/${assetId}/versions/${versionId}/download-url`}/>
  </div>
}
