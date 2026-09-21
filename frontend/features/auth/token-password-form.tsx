"use client";

import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { ApiClientError } from "@/lib/api-client";
import { authApi } from "./auth-api";

type Mode = "reset" | "verify" | "invitation";

export function TokenPasswordForm({ mode, token }: { mode: Mode; token: string }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [show, setShow] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState("");
  const labels = mode === "reset" ? ["Choose a new password", "Update the password and revoke existing sessions."] : mode === "verify" ? ["Activate your account", "Confirm your email and choose a strong password."] : ["Accept your invitation", "Use your existing password, or choose one if this is your first workspace."];

  async function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    if (!token) return setError("This security link is missing its token. Request a new link.");
    if (password !== confirm) return setError("The passwords do not match.");
    if (password.length < 12 || !/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/\d/.test(password)) return setError("Use at least 12 characters with uppercase, lowercase, and a number.");
    setPending(true);
    try {
      const result = mode === "reset" ? await authApi.reset(token, password) : mode === "verify" ? await authApi.verify(token, password) : await authApi.acceptInvitation(token, password);
      setDone(result.message);
    } catch (cause) { setError(cause instanceof ApiClientError ? cause.message : "The security link could not be used."); }
    finally { setPending(false); }
  }

  if (done) return <><h2 className="text-2xl font-bold">You’re all set</h2><Alert tone="success" className="mt-5">{done}</Alert><Link href="/login" className="mt-6 inline-flex min-h-11 items-center rounded-md text-sm font-semibold text-primary hover:underline">Continue to sign in</Link></>;
  return <><h2 className="text-2xl font-bold">{labels[0]}</h2><p className="mt-2 text-sm text-muted-foreground">{labels[1]}</p>{error && <Alert tone="error" className="mt-5">{error}</Alert>}<form className="mt-7 space-y-5" onSubmit={submit} noValidate><FormField label="Password" htmlFor="password" hint="12–72 bytes, including uppercase, lowercase, and a number."><div className="relative"><Input id="password" type={show ? "text" : "password"} autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} className="pr-12" aria-describedby="password-hint" /><button type="button" onClick={() => setShow((value) => !value)} className="absolute inset-y-0 right-0 grid w-12 cursor-pointer place-items-center rounded-r-md text-muted-foreground hover:text-foreground" aria-label={show ? "Hide password" : "Show password"}>{show ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}</button></div></FormField><FormField label="Confirm password" htmlFor="confirm"><Input id="confirm" type={show ? "text" : "password"} autoComplete="new-password" value={confirm} onChange={(event) => setConfirm(event.target.value)} /></FormField><Button type="submit" variant="accent" size="lg" className="w-full" disabled={pending}>{pending && <LoaderCircle aria-hidden="true" className="animate-spin" />}{pending ? "Saving…" : mode === "reset" ? "Reset password" : mode === "verify" ? "Activate account" : "Accept invitation"}</Button></form></>;
}
