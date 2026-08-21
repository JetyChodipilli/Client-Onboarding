"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { ArrowRight, Loader2, LockKeyhole, ShieldCheck } from "lucide-react";
import { useClientAuth } from "@/auth/client-auth-provider";
import { FormMessage } from "@/components/auth/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiClientError } from "@/services/api-client";

export default function ClientLoginPage(){
  const {login,verifyMfa}=useClientAuth(); const router=useRouter(); const search=useSearchParams();
  const [email,setEmail]=useState(""); const [workspace,setWorkspace]=useState(""); const [password,setPassword]=useState("");
  const [challenge,setChallenge]=useState<string|null>(null); const [code,setCode]=useState(""); const [busy,setBusy]=useState(false); const [error,setError]=useState<string|null>(null);
  const next=safeNext(search.get("next"));
  async function submit(event:React.FormEvent){event.preventDefault();setBusy(true);setError(null);try{const result=await login(email,workspace,password);if(result.mfaRequired&&result.challengeToken){setChallenge(result.challengeToken);return;}router.replace(next);}catch(e){setError(message(e,"Sign in could not be completed."));}finally{setBusy(false)}}
  async function submitMfa(event:React.FormEvent){event.preventDefault();if(!challenge)return;setBusy(true);setError(null);try{await verifyMfa(challenge,code);router.replace(next);}catch(e){setError(message(e,"The verification code could not be accepted."));}finally{setBusy(false)}}
  return <AuthFrame eyebrow="Secure client workspace" title={challenge?"Verify it’s you":"Welcome back"} description={challenge?"Enter the six-digit code from your authenticator app.":"Sign in to see exactly what your project needs next."}>
    <form onSubmit={challenge?submitMfa:submit} className="space-y-4">
      {!challenge?<><Field label="Email"><Input type="email" autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} required/></Field><Field label="Workspace"><Input value={workspace} onChange={e=>setWorkspace(e.target.value)} placeholder="northstar-studio" required/></Field><Field label="Password"><Input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} required/></Field><div className="flex items-center justify-between text-sm"><Link className="font-medium text-[hsl(var(--primary))] hover:underline" href="/portal/forgot-password">Forgot password?</Link><span className="text-xs text-[hsl(var(--muted-foreground))]">Invite links can also activate access.</span></div></>:<Field label="Authenticator code"><Input inputMode="numeric" autoComplete="one-time-code" value={code} onChange={e=>setCode(e.target.value.replace(/\D/g,"").slice(0,6))} pattern="\d{6}" required/></Field>}
      {error&&<FormMessage>{error}</FormMessage>}<Button className="w-full" disabled={busy}>{busy?<Loader2 className="size-4 animate-spin"/>:<LockKeyhole className="size-4"/>}{challenge?"Verify and continue":"Sign in securely"}<ArrowRight className="size-4"/></Button>
    </form>
  </AuthFrame>
}

export function AuthFrame({eyebrow,title,description,children}:{eyebrow:string;title:string;description:string;children:React.ReactNode}){return <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,hsl(var(--primary-soft)),transparent_35%),hsl(var(--background))] px-4 py-10 sm:py-16"><div className="mx-auto grid w-full max-w-5xl overflow-hidden rounded-[2rem] border bg-[hsl(var(--surface))] shadow-xl shadow-black/5 lg:grid-cols-[.9fr_1.1fr]"><aside className="hidden min-h-[620px] flex-col justify-between bg-[hsl(var(--ink))] p-10 text-white lg:flex"><div><div className="grid size-11 place-items-center rounded-2xl bg-white/10 text-sm font-black">CO</div><p className="mt-12 text-xs font-bold uppercase tracking-[.18em] text-white/55">Client onboarding</p><h1 className="mt-4 max-w-sm text-4xl font-semibold leading-tight tracking-[-.045em]">Know what’s done, what’s blocked, and what to do next.</h1><p className="mt-5 max-w-sm text-sm leading-7 text-white/65">One secure workspace for the requirements your delivery team has shared with you.</p></div><div className="space-y-3 text-sm text-white/70"><p className="flex items-center gap-2"><ShieldCheck className="size-4 text-emerald-300"/>Project-scoped access</p><p className="flex items-center gap-2"><ShieldCheck className="size-4 text-emerald-300"/>Internal-only work stays private</p></div></aside><section className="flex min-h-[620px] items-center p-6 sm:p-10 lg:p-14"><div className="w-full"><p className="text-xs font-bold uppercase tracking-[.16em] text-[hsl(var(--primary))]">{eyebrow}</p><h2 className="mt-3 text-3xl font-semibold tracking-[-.04em]">{title}</h2><p className="mt-3 max-w-md text-sm leading-6 text-[hsl(var(--muted-foreground))]">{description}</p><div className="mt-8">{children}</div></div></section></div></main>}
function Field({label,children}:{label:string;children:React.ReactNode}){return <div className="space-y-2"><Label>{label}</Label>{children}</div>}
function message(error:unknown,fallback:string){return error instanceof ApiClientError?error.message:fallback}
function safeNext(value:string|null){return value&&value.startsWith("/portal")&&!value.startsWith("//")?value:"/portal"}
