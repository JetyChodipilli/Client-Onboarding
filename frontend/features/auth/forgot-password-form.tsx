"use client";

import { LoaderCircle } from "lucide-react";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { ApiClientError } from "@/lib/api-client";
import { authApi } from "./auth-api";

export function ForgotPasswordForm() {
  const [email, setEmail] = useState("");
  const [slug, setSlug] = useState("");
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault(); setPending(true); setError("");
    try { const result = await authApi.forgot(email, slug); setMessage(result.message); }
    catch (cause) { setError(cause instanceof ApiClientError ? cause.message : "The request could not be completed."); }
    finally { setPending(false); }
  }

  if (message) return <><h2 className="text-2xl font-bold">Check your email</h2><Alert tone="success" className="mt-5">{message}</Alert><Link href="/login" className="mt-6 inline-flex min-h-11 items-center rounded-md text-sm font-semibold text-primary hover:underline">Return to sign in</Link></>;
  return <><h2 className="text-2xl font-bold">Reset your password</h2><p className="mt-2 text-sm text-muted-foreground">We’ll send a single-use link if the account is eligible.</p>{error && <Alert tone="error" className="mt-5">{error}</Alert>}<form className="mt-7 space-y-5" onSubmit={submit}><FormField label="Work email" htmlFor="email"><Input required id="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} /></FormField><FormField label="Organization slug" htmlFor="slug"><Input required id="slug" autoComplete="organization" value={slug} onChange={(event) => setSlug(event.target.value.toLowerCase())} /></FormField><Button type="submit" variant="accent" size="lg" className="w-full" disabled={pending}>{pending && <LoaderCircle aria-hidden="true" className="animate-spin" />}{pending ? "Sending…" : "Send reset link"}</Button><Link href="/login" className="block text-center text-sm text-primary hover:underline">Return to sign in</Link></form></>;
}
