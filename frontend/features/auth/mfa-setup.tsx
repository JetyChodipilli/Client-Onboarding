"use client";

import { useState } from "react";
import { Copy, KeyRound, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormMessage } from "@/components/auth/form-message";
import { apiRequest, ApiClientError } from "@/services/api-client";
import type { AuthResponse } from "@/types/auth";

export function MfaSetup({ setupToken, onComplete }: { setupToken: string; onComplete: (result: AuthResponse) => void }) {
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function begin() {
    setBusy(true); setError(null);
    try {
      setSetup(await apiRequest("/auth/mfa/setup/start", { method: "POST", body: JSON.stringify({ setupToken }) }));
    } catch (e) { setError(e instanceof ApiClientError ? e.message : "MFA setup could not be started."); }
    finally { setBusy(false); }
  }

  async function confirm(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      const result = await apiRequest<AuthResponse>("/auth/mfa/setup/confirm", { method: "POST", body: JSON.stringify({ setupToken, code }) });
      onComplete(result);
    } catch (e) { setError(e instanceof ApiClientError ? e.message : "The verification code could not be confirmed."); }
    finally { setBusy(false); }
  }

  if (!setup) {
    return <div className="space-y-4">
      <FormMessage tone="info">Privileged access requires multi-factor authentication before this session can continue.</FormMessage>
      <Button className="w-full" onClick={begin} disabled={busy}>{busy ? <Loader2 className="size-4 animate-spin" /> : <KeyRound className="size-4" />} Set up authenticator</Button>
    </div>;
  }

  return <form className="space-y-5" onSubmit={confirm}>
    <div className="rounded-xl border bg-white p-4">
      <p className="text-sm font-semibold">Add this account to your authenticator app</p>
      <p className="mt-1 text-xs leading-5 text-[hsl(var(--muted-foreground))]">Use the setup key below if your authenticator cannot scan an otpauth link.</p>
      <div className="mt-3 flex items-center gap-2 rounded-lg bg-[hsl(var(--surface-subtle))] px-3 py-2 font-mono text-xs break-all">
        <span className="min-w-0 flex-1">{setup.secret}</span>
        <button type="button" className="rounded-md p-1.5 hover:bg-white" aria-label="Copy MFA setup key" onClick={() => navigator.clipboard?.writeText(setup.secret)}><Copy className="size-4" /></button>
      </div>
    </div>
    <div className="space-y-2"><Label htmlFor="mfa-code">6-digit code</Label><Input id="mfa-code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))} required /></div>
    {error && <FormMessage>{error}</FormMessage>}
    <Button className="w-full" disabled={busy || code.length !== 6}>{busy && <Loader2 className="size-4 animate-spin" />} Verify and continue</Button>
  </form>;
}
