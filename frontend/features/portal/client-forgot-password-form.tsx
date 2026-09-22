"use client";
import { FormEvent, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { ApiClientError } from "@/lib/api-client";
import { portalApi } from "./portal-api";

export function ClientForgotPasswordForm({ organization = "" }: { organization?: string }) {
  const [email, setEmail] = useState(""); const [slug, setSlug] = useState(organization);
  const [pending, setPending] = useState(false); const [message, setMessage] = useState(""); const [error, setError] = useState("");
  async function submit(event: FormEvent) { event.preventDefault(); setPending(true); setError(""); try { setMessage((await portalApi.forgot(email, slug)).message); } catch (cause) { setError(cause instanceof ApiClientError ? cause.message : "The reset request could not be sent."); } finally { setPending(false); } }
  return <div><h2 className="text-2xl font-bold">Reset your client password</h2><p className="mt-2 text-sm text-muted-foreground">We return the same response whether an account exists, protecting account privacy.</p>{message && <Alert tone="success" className="mt-5">{message}</Alert>}{error && <Alert tone="error" className="mt-5">{error}</Alert>}<form onSubmit={submit} className="mt-7 space-y-5"><FormField label="Work email" htmlFor="client-reset-email"><Input id="client-reset-email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></FormField><FormField label="Organization slug" htmlFor="client-reset-organization"><Input id="client-reset-organization" value={slug} onChange={(event) => setSlug(event.target.value.toLowerCase())} required /></FormField><Button type="submit" variant="accent" size="lg" className="w-full" disabled={pending}>{pending ? "Sending…" : "Send reset link"}</Button></form></div>;
}
