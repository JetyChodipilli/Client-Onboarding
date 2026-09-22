"use client";

import { CheckCircle2, Eye, EyeOff, LoaderCircle, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { ApiClientError } from "@/lib/api-client";
import { portalApi } from "./portal-api";
import type { PublicInvitation } from "./types";

export function ClientInvitationForm({ token }: { token: string }) {
  const [invitation, setInvitation] = useState<PublicInvitation>();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [show, setShow] = useState(false);
  const [pending, setPending] = useState(Boolean(token));
  const [error, setError] = useState(token ? "" : "This invitation link is missing its secure token.");
  const [done, setDone] = useState<{ message: string; organizationSlug: string; projectName: string }>();

  useEffect(() => {
    if (!token) return;
    portalApi.inspect(token).then(setInvitation).catch((cause) => setError(cause instanceof ApiClientError ? cause.message : "This invitation could not be verified.")).finally(() => setPending(false));
  }, [token]);

  async function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    if (password !== confirm) return setError("The passwords do not match.");
    if (password.length < 12 || !/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/\d/.test(password)) return setError("Use at least 12 characters with uppercase, lowercase, and a number.");
    setPending(true);
    try { setDone(await portalApi.accept(token, password)); }
    catch (cause) { setError(cause instanceof ApiClientError ? cause.message : "This invitation could not be accepted."); }
    finally { setPending(false); }
  }

  if (pending && !invitation) return <div aria-busy="true"><div className="h-7 w-48 animate-pulse rounded bg-muted" /><div className="mt-4 h-24 animate-pulse rounded-lg bg-muted" /><p className="mt-5 text-sm text-muted-foreground">Verifying your private invitation…</p></div>;
  if (done) return <div><CheckCircle2 aria-hidden="true" className="size-8 text-success" /><h2 className="mt-5 text-2xl font-bold">Your portal is ready</h2><p className="mt-2 text-sm text-muted-foreground">{done.message} Continue to {done.projectName} using your verified account.</p><Link href={`/client/login?organization=${encodeURIComponent(done.organizationSlug)}`} className="mt-6 inline-flex min-h-11 items-center rounded-md bg-accent px-5 text-sm font-semibold text-accent-foreground transition-colors hover:bg-accent/90">Continue to client sign in</Link></div>;
  if (!invitation || invitation.status !== "VALID") return <div><h2 className="text-2xl font-bold">This invitation is unavailable</h2><Alert tone="error" className="mt-5">{error || `This link is ${invitation?.status.toLowerCase()}. Ask your project team to resend it.`}</Alert><p className="mt-5 text-sm text-muted-foreground">For security, expired, accepted, and revoked links cannot be reused.</p></div>;
  return <div><div className="flex items-center gap-2 text-sm font-semibold text-success"><ShieldCheck aria-hidden="true" className="size-4" />Invitation verified</div><h2 className="mt-4 text-2xl font-bold">Join {invitation.projectName}</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">{invitation.organizationName} invited {invitation.email} to the secure {invitation.clientName} onboarding portal.</p><div className="mt-5 rounded-lg border bg-muted/50 p-4 text-sm"><dl className="grid gap-3 sm:grid-cols-2"><div><dt className="font-semibold text-muted-foreground">Project</dt><dd className="mt-1 font-semibold">{invitation.projectName}</dd></div><div><dt className="font-semibold text-muted-foreground">Link expires</dt><dd className="mt-1 font-semibold">{new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(invitation.expiresAt))}</dd></div></dl></div>{error && <Alert tone="error" className="mt-5">{error}</Alert>}<form className="mt-6 space-y-5" onSubmit={submit} noValidate><FormField label="Create or confirm your password" htmlFor="client-password" hint="Existing client users must enter their current password."><div className="relative"><Input id="client-password" type={show ? "text" : "password"} autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} className="pr-12" /><button type="button" onClick={() => setShow((value) => !value)} className="absolute inset-y-0 right-0 grid w-12 cursor-pointer place-items-center rounded-r-md text-muted-foreground transition-colors hover:text-foreground" aria-label={show ? "Hide password" : "Show password"}>{show ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}</button></div></FormField><FormField label="Confirm password" htmlFor="client-confirm"><Input id="client-confirm" type={show ? "text" : "password"} autoComplete="new-password" value={confirm} onChange={(event) => setConfirm(event.target.value)} /></FormField><Button type="submit" variant="accent" size="lg" className="w-full" disabled={pending}>{pending && <LoaderCircle aria-hidden="true" className="animate-spin" />}{pending ? "Activating…" : "Activate secure portal"}</Button></form></div>;
}
