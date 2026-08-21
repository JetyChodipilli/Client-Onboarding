"use client";
import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2, Mail } from "lucide-react";
import { AuthFrame } from "../login/page";
import { FormMessage } from "@/components/auth/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest,ApiClientError } from "@/services/api-client";
export default function Forgot(){const[email,setEmail]=useState("");const[workspace,setWorkspace]=useState("");const[busy,setBusy]=useState(false);const[done,setDone]=useState(false);const[error,setError]=useState<string|null>(null);async function submit(e:React.FormEvent){e.preventDefault();setBusy(true);setError(null);try{await apiRequest("/client-auth/forgot-password",{method:"POST",body:JSON.stringify({email,organizationSlug:workspace})});setDone(true)}catch(x){setError(x instanceof ApiClientError?x.message:"Request could not be completed.")}finally{setBusy(false)}}return <AuthFrame eyebrow="Account recovery" title="Reset your password" description="For privacy, the same confirmation is shown whether or not an account exists.">{done?<div className="rounded-2xl border bg-[hsl(var(--success-soft))] p-5"><p className="font-semibold">Check your inbox</p><p className="mt-2 text-sm leading-6 text-[hsl(var(--muted-foreground))]">If this email has active access to that workspace, we sent a short-lived reset link.</p><Button asChild variant="secondary" className="mt-4"><Link href="/portal/login"><ArrowLeft className="size-4"/>Back to sign in</Link></Button></div>:<form onSubmit={submit} className="space-y-4"><div className="space-y-2"><Label>Email</Label><Input type="email" value={email} onChange={e=>setEmail(e.target.value)} required/></div><div className="space-y-2"><Label>Workspace</Label><Input value={workspace} onChange={e=>setWorkspace(e.target.value)} required/></div>{error&&<FormMessage>{error}</FormMessage>}<Button className="w-full" disabled={busy}>{busy?<Loader2 className="size-4 animate-spin"/>:<Mail className="size-4"/>}Send secure reset link</Button></form>}</AuthFrame>}
