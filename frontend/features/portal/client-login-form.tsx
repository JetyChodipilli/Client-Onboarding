"use client";

import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { ApiClientError } from "@/lib/api-client";
import { portalApi } from "./portal-api";

export function ClientLoginForm({ organization = "" }: { organization?: string }) {
  const [email, setEmail] = useState(""); const [slug, setSlug] = useState(organization);
  const [password, setPassword] = useState(""); const [show, setShow] = useState(false);
  const [pending, setPending] = useState(false); const [error, setError] = useState("");
  const router = useRouter();
  async function submit(event: FormEvent) { event.preventDefault(); setError(""); setPending(true); try { await portalApi.login(email, password, slug); router.push("/portal"); } catch (cause) { setError(cause instanceof ApiClientError ? cause.message : "Sign in could not be completed."); } finally { setPending(false); } }
  return <div><h2 className="text-2xl font-bold">Sign in to your client portal</h2><p className="mt-2 text-sm text-muted-foreground">Use the email and workspace address from your invitation.</p>{error && <Alert tone="error" className="mt-5">{error}</Alert>}<form onSubmit={submit} className="mt-7 space-y-5"><FormField label="Work email" htmlFor="client-email"><Input id="client-email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></FormField><FormField label="Organization slug" htmlFor="client-organization" hint="Shown in your invitation link."><Input id="client-organization" autoComplete="organization" value={slug} onChange={(event) => setSlug(event.target.value.toLowerCase())} required /></FormField><FormField label="Password" htmlFor="client-login-password"><div className="relative"><Input id="client-login-password" type={show ? "text" : "password"} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} className="pr-12" required /><button type="button" onClick={() => setShow((value) => !value)} className="absolute inset-y-0 right-0 grid w-12 cursor-pointer place-items-center text-muted-foreground transition-colors hover:text-foreground" aria-label={show ? "Hide password" : "Show password"}>{show ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}</button></div></FormField><Button type="submit" variant="accent" size="lg" className="w-full" disabled={pending}>{pending && <LoaderCircle aria-hidden="true" className="animate-spin" />}{pending ? "Signing in…" : "Open client portal"}</Button><Link href={`/client/forgot-password?organization=${encodeURIComponent(slug)}`} className="flex min-h-11 items-center justify-center rounded-md text-sm font-semibold text-primary hover:underline">Forgot password?</Link></form></div>;
}
