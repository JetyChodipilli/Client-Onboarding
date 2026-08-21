"use client";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { CheckCircle2, Loader2 } from "lucide-react";
import { AuthFrame } from "../login/page";
import { FormMessage } from "@/components/auth/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiRequest,ApiClientError } from "@/services/api-client";
export default function Reset(){const token=useSearchParams().get("token")??"";const[p,setP]=useState("");const[c,setC]=useState("");const[busy,setBusy]=useState(false);const[done,setDone]=useState(false);const[error,setError]=useState<string|null>(null);async function submit(e:React.FormEvent){e.preventDefault();if(p!==c){setError("Passwords do not match.");return}setBusy(true);setError(null);try{await apiRequest("/client-auth/reset-password",{method:"POST",body:JSON.stringify({token,newPassword:p})});setDone(true)}catch(x){setError(x instanceof ApiClientError?x.message:"Password could not be reset.")}finally{setBusy(false)}}return <AuthFrame eyebrow="Account recovery" title={done?"Password updated":"Choose a new password"} description="Reset links are single-use and expire automatically.">{done?<div className="rounded-2xl border p-5"><CheckCircle2 className="size-7 text-[hsl(var(--success))]"/><p className="mt-3 font-semibold">Your password has been changed.</p><Button asChild className="mt-5"><Link href="/portal/login">Sign in</Link></Button></div>:<form onSubmit={submit} className="space-y-4"><div className="space-y-2"><Label>New password</Label><Input type="password" autoComplete="new-password" value={p} onChange={e=>setP(e.target.value)} minLength={12} required/></div><div className="space-y-2"><Label>Confirm password</Label><Input type="password" autoComplete="new-password" value={c} onChange={e=>setC(e.target.value)} minLength={12} required/></div>{!token&&<FormMessage>This reset link is missing its security token.</FormMessage>}{error&&<FormMessage>{error}</FormMessage>}<Button className="w-full" disabled={busy||!token}>{busy&&<Loader2 className="size-4 animate-spin"/>}Update password</Button></form>}</AuthFrame>}
