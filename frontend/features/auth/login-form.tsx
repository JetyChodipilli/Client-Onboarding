"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ArrowRight, Loader2, LockKeyhole } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormMessage } from "@/components/auth/form-message";
import { MfaSetup } from "@/features/auth/mfa-setup";
import { useAuth } from "@/auth/auth-provider";
import { apiRequest, ApiClientError } from "@/services/api-client";
import type { AuthResponse } from "@/types/auth";

type Stage = { kind: "login" } | { kind: "mfa"; token: string } | { kind: "setup"; token: string };

export function LoginForm() {
  const router = useRouter();
  const { login, completeAuth } = useAuth();
  const [email, setEmail] = useState(""); const [workspace, setWorkspace] = useState(""); const [password, setPassword] = useState("");
  const [code, setCode] = useState(""); const [stage, setStage] = useState<Stage>({ kind: "login" });
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null);

  function finish(result: AuthResponse) {
    completeAuth(result);
    if (result.accessToken) router.replace("/app");
    else if (result.mfaSetupRequired && result.challengeToken) setStage({ kind: "setup", token: result.challengeToken });
    else if (result.mfaRequired && result.challengeToken) setStage({ kind: "mfa", token: result.challengeToken });
  }

  async function submitLogin(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(null);
    try { finish(await login(email, workspace, password)); }
    catch (e) { setError(e instanceof ApiClientError ? e.message : "Sign in could not be completed."); }
    finally { setBusy(false); }
  }

  async function submitMfa(event: React.FormEvent) {
    event.preventDefault(); if (stage.kind !== "mfa") return; setBusy(true); setError(null);
    try { finish(await apiRequest<AuthResponse>("/auth/mfa/verify", { method: "POST", body: JSON.stringify({ challengeToken: stage.token, code }) })); }
    catch (e) { setError(e instanceof ApiClientError ? e.message : "The verification code could not be confirmed."); }
    finally { setBusy(false); }
  }

  if (stage.kind === "setup") return <MfaSetup setupToken={stage.token} onComplete={finish} />;
  if (stage.kind === "mfa") return <form className="space-y-5" onSubmit={submitMfa}>
    <FormMessage tone="info">Enter the code from your authenticator app. This challenge expires shortly.</FormMessage>
    <div className="space-y-2"><Label htmlFor="code">Verification code</Label><Input id="code" autoFocus inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))} maxLength={6} required /></div>
    {error && <FormMessage>{error}</FormMessage>}
    <Button className="w-full" disabled={busy || code.length !== 6}>{busy ? <Loader2 className="size-4 animate-spin" /> : <LockKeyhole className="size-4" />} Verify identity</Button>
    <button type="button" onClick={() => { setStage({ kind: "login" }); setCode(""); setError(null); }} className="w-full text-center text-sm font-semibold text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))]">Back to sign in</button>
  </form>;

  return <form className="space-y-5" onSubmit={submitLogin} noValidate>
    <div className="space-y-2"><Label htmlFor="workspace">Workspace</Label><Input id="workspace" name="workspace" autoComplete="organization" placeholder="acme-studio" value={workspace} onChange={(e) => setWorkspace(e.target.value)} required /><p className="text-xs text-[hsl(var(--muted-foreground))]">Your organization workspace slug.</p></div>
    <div className="space-y-2"><Label htmlFor="email">Work email</Label><Input id="email" name="email" type="email" autoComplete="email" placeholder="you@company.com" value={email} onChange={(e) => setEmail(e.target.value)} required /></div>
    <div className="space-y-2"><div className="flex items-center justify-between gap-3"><Label htmlFor="password">Password</Label><Link href="/forgot-password" className="text-xs font-semibold text-[hsl(var(--primary))] hover:underline">Forgot password?</Link></div><Input id="password" name="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></div>
    {error && <FormMessage>{error}</FormMessage>}
    <Button className="w-full" size="lg" disabled={busy}>{busy ? <Loader2 className="size-4 animate-spin" /> : <ArrowRight className="size-4" />} Sign in securely</Button>
    <p className="text-center text-xs leading-5 text-[hsl(var(--muted-foreground))]">Access is limited to the workspace and permissions assigned to your account.</p>
  </form>;
}
