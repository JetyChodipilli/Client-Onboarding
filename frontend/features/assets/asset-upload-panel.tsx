"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { CheckCircle2, Download, FileUp, Loader2, RefreshCw, ShieldCheck, TriangleAlert } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/services/api-client";
import type { AssetDownloadUrl, AssetStep, AssetUploadUrl, AssetVersion } from "@/types/assets";

type AuthorizedRequest = <T>(path: string, init?: RequestInit) => Promise<T>;

type Props = {
  authorizedRequest: AuthorizedRequest;
  resourcePath: string;
  downloadPath: (assetId: string, versionId: string) => string;
  disabled?: boolean;
  compact?: boolean;
  onCleanVersion?: (versionId: string) => void;
};

const terminalUploadErrors = new Set(["QUARANTINED", "REJECTED"]);
const scanning = new Set(["UPLOADED", "SCANNING"]);

export function AssetUploadPanel({ authorizedRequest, resourcePath, downloadPath, disabled=false, compact=false, onCleanVersion }: Props) {
  const [data,setData]=useState<AssetStep|null>(null);
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState<string|null>(null);
  const [notice,setNotice]=useState<string|null>(null);
  const fileRef=useRef<HTMLInputElement|null>(null);
  const lastCleanReported=useRef<string|null>(null);

  const load=useCallback(async(silent=false)=>{
    if(!silent)setLoading(true);
    try{const value=await authorizedRequest<AssetStep>(resourcePath);setData(value);setError(null)}
    catch(e){if(!silent)setError(message(e,"Secure file requirement could not be loaded."))}
    finally{if(!silent)setLoading(false)}
  },[authorizedRequest,resourcePath]);

  useEffect(()=>{void load()},[load]);
  useEffect(()=>{
    if(!data||!scanning.has(data.status))return;
    const timer=window.setInterval(()=>void load(true),4000);
    return()=>window.clearInterval(timer);
  },[data,load]);

  const current=useMemo(()=>data?.versions.find(v=>v.id===data.currentVersionId)??data?.versions[0]??null,[data]);
  useEffect(()=>{if(current?.status!=="CLEAN"||lastCleanReported.current===current.id)return;lastCleanReported.current=current.id;onCleanVersion?.(current.id)},[current?.id,current?.status,onCleanVersion]);

  async function upload(file:File){
    if(!data||disabled)return;
    setBusy(true);setError(null);setNotice(null);
    try{
      if(file.size<=0||file.size>data.maxFileSizeBytes)throw new Error(`Choose a file up to ${formatBytes(data.maxFileSizeBytes)}.`);
      if(!data.allowedMimeTypes.includes(file.type))throw new Error("This file type is not permitted for this requirement.");
      const idempotency=`upload:${crypto.randomUUID()}`;
      const authorization=await authorizedRequest<AssetUploadUrl>(`${resourcePath}/upload-url`,{method:"POST",headers:{"Idempotency-Key":idempotency},body:JSON.stringify({filename:file.name,contentType:file.type,sizeBytes:file.size,sha256:null})});
      const uploadHeaders=new Headers();
      Object.entries(authorization.headers).forEach(([name,values])=>uploadHeaders.set(name,values.join(",")));
      if(!uploadHeaders.has("content-type"))uploadHeaders.set("content-type",file.type);
      const stored=await fetch(authorization.uploadUrl,{method:authorization.method||"PUT",headers:uploadHeaders,body:file,credentials:"omit"});
      if(!stored.ok)throw new Error("Secure storage rejected the upload. Try again with a fresh upload authorization.");
      const completed=await authorizedRequest<AssetStep>(`${resourcePath}/complete-upload`,{method:"POST",body:JSON.stringify({assetVersionId:authorization.assetVersionId})});
      setData(completed);setNotice("Upload received. Security scanning must finish before the file is available.");
    }catch(e){setError(message(e,"The file could not be uploaded."))}
    finally{setBusy(false);if(fileRef.current)fileRef.current.value=""}
  }

  async function download(version:AssetVersion){
    if(!data?.assetId)return;
    setError(null);
    try{const result=await authorizedRequest<AssetDownloadUrl>(downloadPath(data.assetId,version.id));const url=safeDownloadUrl(result.url);if(!url)throw new Error("The storage service returned an unsafe download URL.");window.location.assign(url)}
    catch(e){setError(message(e,"A secure download link could not be created."))}
  }

  if(loading)return <div className="flex items-center gap-2 rounded-xl border p-4 text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="size-4 animate-spin"/>Loading secure upload…</div>;
  if(error&&!data)return <div className="rounded-xl border border-[hsl(var(--danger)/.3)] bg-[hsl(var(--danger)/.05)] p-4 text-sm"><p>{error}</p><Button type="button" variant="secondary" size="sm" className="mt-3" onClick={()=>void load()}><RefreshCw className="size-4"/>Retry</Button></div>;
  if(!data)return null;
  const blocked=disabled||["UNDER_REVIEW","APPROVED"].includes(data.status)||scanning.has(data.status);
  return <div className={compact?"space-y-3 rounded-xl border bg-[hsl(var(--surface-subtle))] p-4":"space-y-5 rounded-2xl border bg-[hsl(var(--surface))] p-5 sm:p-6"}>
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="font-semibold">{data.name}</p>{data.description&&<p className="mt-1 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{data.description}</p>}</div><AssetStatus status={data.status}/></div>
    <div className="flex flex-wrap gap-2 text-xs"><Badge variant="neutral">Max {formatBytes(data.maxFileSizeBytes)}</Badge><Badge variant="neutral">{data.allowedMimeTypes.length} allowed file types</Badge>{data.requiresReview&&<Badge variant="info">Team review required</Badge>}</div>
    {error&&<p role="alert" className="rounded-xl bg-[hsl(var(--danger)/.08)] px-3 py-2 text-sm text-[hsl(var(--danger))]">{error}</p>}
    {notice&&<p className="rounded-xl bg-[hsl(var(--success)/.08)] px-3 py-2 text-sm text-[hsl(var(--success))]">{notice}</p>}
    {scanning.has(data.status)&&<div className="flex gap-3 rounded-xl border border-[hsl(var(--info)/.25)] bg-[hsl(var(--info)/.06)] p-4"><ShieldCheck className="mt-0.5 size-5 shrink-0 text-[hsl(var(--info))]"/><div><p className="font-medium">Security checks in progress</p><p className="mt-1 text-sm text-[hsl(var(--muted-foreground))]">We verify the file signature, configured MIME policy and malware scan before anyone can download it.</p></div></div>}
    {terminalUploadErrors.has(data.status)&&<div className="flex gap-3 rounded-xl border border-[hsl(var(--danger)/.25)] bg-[hsl(var(--danger)/.06)] p-4"><TriangleAlert className="mt-0.5 size-5 shrink-0 text-[hsl(var(--danger))]"/><div><p className="font-medium">This file cannot be used</p><p className="mt-1 text-sm text-[hsl(var(--muted-foreground))]">Upload a replacement. Unsafe files are never exposed through normal download links.</p></div></div>}
    {!blocked&&<div><input ref={fileRef} type="file" className="sr-only" aria-label={`Upload ${data.name}`} accept={data.allowedMimeTypes.join(",")} onChange={e=>{const f=e.target.files?.[0];if(f)void upload(f)}}/><Button type="button" onClick={()=>fileRef.current?.click()} disabled={busy}>{busy?<Loader2 className="size-4 animate-spin"/>:<FileUp className="size-4"/>}{data.versions.length?"Upload replacement":"Choose file"}</Button><p className="mt-2 text-xs text-[hsl(var(--muted-foreground))]">The browser uploads directly to private object storage using a short-lived authorization. Raw bucket URLs are never exposed.</p></div>}
    {data.versions.length>0&&<div className="overflow-hidden rounded-xl border"><div className="border-b bg-[hsl(var(--surface-subtle))] px-4 py-2.5 text-xs font-semibold uppercase tracking-[.1em] text-[hsl(var(--muted-foreground))]">File versions</div><div className="divide-y">{data.versions.map(v=><div key={v.id} className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"><div className="min-w-0"><p className="truncate text-sm font-medium">v{v.versionNumber} · {v.filename}</p><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">{formatBytes(v.actualSizeBytes??v.expectedSizeBytes)} · {v.detectedMimeType??v.declaredMimeType}</p>{v.rejectionReason&&<p className="mt-1 text-xs text-[hsl(var(--danger))]">{v.rejectionReason}</p>}</div><div className="flex items-center gap-2"><VersionStatus status={v.status}/>{v.status==="CLEAN"&&<Button type="button" variant="ghost" size="sm" onClick={()=>void download(v)}><Download className="size-4"/>Download</Button>}</div></div>)}</div></div>}
  </div>;
}

function AssetStatus({status}:{status:AssetStep["status"]}){const complete=status==="APPROVED";return <Badge variant={complete?"success":terminalUploadErrors.has(status)?"danger":scanning.has(status)?"info":"neutral"}>{complete&&<CheckCircle2 className="size-3"/>}{status.replaceAll("_"," ")}</Badge>}
function VersionStatus({status}:{status:AssetVersion["status"]}){return <Badge variant={status==="CLEAN"?"success":status==="QUARANTINED"||status==="REJECTED"?"danger":status==="SCANNING"||status==="SCAN_PENDING"?"info":"neutral"}>{status.replaceAll("_"," ")}</Badge>}
function formatBytes(value:number){if(value<1024)return `${value} B`;if(value<1024**2)return `${(value/1024).toFixed(1)} KB`;if(value<1024**3)return `${(value/1024**2).toFixed(1)} MB`;return `${(value/1024**3).toFixed(1)} GB`}
function message(e:unknown,fallback:string){return e instanceof ApiClientError?e.message:e instanceof Error?e.message:fallback}
function safeDownloadUrl(value:string){try{const url=new URL(value,window.location.origin);if(url.protocol==="https:")return url.toString();if((url.hostname==="localhost"||url.hostname==="127.0.0.1")&&url.protocol==="http:")return url.toString();return null}catch{return null}}
