"use client";
import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2, Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormMessage } from "@/components/auth/form-message";
import { apiRequest, ApiClientError } from "@/services/api-client";

export function ForgotPasswordForm() {
  const [email,setEmail]=useState(""); const [organizationSlug,setOrganizationSlug]=useState(""); const [busy,setBusy]=useState(false); const [message,setMessage]=useState<string|null>(null); const [error,setError]=useState<string|null>(null);
  async function submit(e:React.FormEvent){e.preventDefault();setBusy(true);setError(null);try{const result=await apiRequest<{message:string}>("/auth/forgot-password",{method:"POST",body:JSON.stringify({email,organizationSlug})});setMessage(result.message);}catch(err){setError(err instanceof ApiClientError?err.message:"Request could not be completed.");}finally{setBusy(false)}}
  return <form className="space-y-5" onSubmit={submit}>
    <div className="space-y-2"><Label htmlFor="workspace">Workspace</Label><Input id="workspace" value={organizationSlug} onChange={e=>setOrganizationSlug(e.target.value)} placeholder="acme-studio" required /></div>
    <div className="space-y-2"><Label htmlFor="email">Work email</Label><Input id="email" type="email" autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} required /></div>
    {message&&<FormMessage tone="success">{message}</FormMessage>}{error&&<FormMessage>{error}</FormMessage>}
    <Button className="w-full" size="lg" disabled={busy}>{busy?<Loader2 className="size-4 animate-spin"/>:<Mail className="size-4"/>} Send reset instructions</Button>
    <Button variant="ghost" className="w-full" asChild><Link href="/login"><ArrowLeft className="size-4"/>Back to sign in</Link></Button>
  </form>
}
