"use client";

import { Eye, EyeOff, LoaderCircle, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useRef, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { ApiClientError } from "@/lib/api-client";
import { authApi } from "./auth-api";
import type { LoginResult } from "./types";

type Errors = Partial<Record<"email" | "password" | "organizationSlug" | "code", string>>;

export function LoginForm() {
  const [stage, setStage] = useState<"login" | "mfa" | "recovery">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [organizationSlug, setOrganizationSlug] = useState("");
  const [code, setCode] = useState("");
  const [challenge, setChallenge] = useState<LoginResult>();
  const [errors, setErrors] = useState<Errors>({});
  const [requestError, setRequestError] = useState("");
  const [pending, setPending] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const summaryRef = useRef<HTMLParagraphElement>(null);
  const router = useRouter();

  async function submitLogin(event: FormEvent) {
    event.preventDefault();
    const next: Errors = {};
    if (!email.includes("@")) next.email = "Enter a valid email address.";
    if (!password) next.password = "Enter your password.";
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(organizationSlug)) next.organizationSlug = "Use your organization slug, for example acme-agency.";
    if (Object.keys(next).length) return showErrors(next);
    setPending(true); setRequestError("");
    try {
      const result = await authApi.login(email, password, organizationSlug);
      if (result.state === "AUTHENTICATED") return router.push("/app");
      setChallenge(result); setStage("mfa"); setCode(""); setErrors({});
    } catch (error) {
      setRequestError(error instanceof ApiClientError ? `${error.message}${error.requestId ? ` Request ${error.requestId}.` : ""}` : "Sign in could not be completed. Try again.");
    } finally { setPending(false); }
  }

  async function submitMfa(event: FormEvent) {
    event.preventDefault();
    if (!code.trim()) return showErrors({ code: "Enter the six-digit code or a recovery code." });
    if (!challenge?.challengeToken) return setRequestError("The verification session expired. Return to sign in.");
    setPending(true); setRequestError("");
    try {
      const result = await authApi.completeMfa(challenge.challengeToken, code.trim());
      if (result.recoveryCodes.length) { setChallenge(result); setStage("recovery"); }
      else router.push("/app");
    } catch (error) {
      setRequestError(error instanceof ApiClientError ? error.message : "Verification could not be completed.");
    } finally { setPending(false); }
  }

  function showErrors(next: Errors) {
    setErrors(next);
    requestAnimationFrame(() => summaryRef.current?.focus());
  }

  if (stage === "recovery" && challenge) {
    return (
      <div>
        <ShieldCheck aria-hidden="true" className="size-7 text-success" />
        <h2 className="mt-5 text-2xl font-bold tracking-tight">Save your recovery codes</h2>
        <p className="mt-2 text-sm text-muted-foreground">Store these one-time codes somewhere secure. They will not be shown again.</p>
        <div className="mt-6 grid grid-cols-2 gap-2 rounded-lg border bg-muted/60 p-4 font-mono text-sm tabular-nums" aria-label="Recovery codes">
          {challenge.recoveryCodes.map((item) => <code key={item}>{item}</code>)}
        </div>
        <Button className="mt-6 w-full" onClick={() => router.push("/app")}>I saved these codes</Button>
      </div>
    );
  }

  return (
    <div>
      <h2 className="text-2xl font-bold tracking-tight">{stage === "login" ? "Sign in to your workspace" : challenge?.state === "MFA_ENROLLMENT_REQUIRED" ? "Secure your account" : "Verify it’s you"}</h2>
      <p className="mt-2 text-sm text-muted-foreground">{stage === "login" ? "Use the workspace address supplied by your administrator." : "Use an authenticator code. A saved recovery code also works."}</p>
      {requestError && <Alert tone="error" className="mt-5">{requestError}</Alert>}
      {Object.keys(errors).length > 0 && (
        <div role="alert" className="mt-5 rounded-lg border border-danger/30 bg-danger/8 p-4">
          <p ref={summaryRef} tabIndex={-1} className="text-sm font-semibold">Check the highlighted fields.</p>
          <ul className="mt-2 list-disc pl-5 text-sm text-danger">{Object.entries(errors).map(([field, message]) => <li key={field}><a href={`#${field}`} className="underline">{message}</a></li>)}</ul>
        </div>
      )}

      {stage === "login" ? (
        <form className="mt-7 space-y-5" onSubmit={submitLogin} noValidate>
          <FormField label="Work email" htmlFor="email" error={errors.email}><Input id="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} aria-invalid={Boolean(errors.email)} aria-describedby={errors.email ? "email-error" : undefined} /></FormField>
          <FormField label="Organization slug" htmlFor="organizationSlug" hint="The short workspace name in your invitation." error={errors.organizationSlug}><Input id="organizationSlug" autoComplete="organization" value={organizationSlug} onChange={(event) => setOrganizationSlug(event.target.value.toLowerCase())} aria-invalid={Boolean(errors.organizationSlug)} aria-describedby={`organizationSlug-hint${errors.organizationSlug ? " organizationSlug-error" : ""}`} /></FormField>
          <FormField label="Password" htmlFor="password" error={errors.password}>
            <div className="relative"><Input id="password" type={showPassword ? "text" : "password"} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} className="pr-12" aria-invalid={Boolean(errors.password)} aria-describedby={errors.password ? "password-error" : undefined} /><button type="button" className="absolute inset-y-0 right-0 grid w-12 cursor-pointer place-items-center rounded-r-md text-muted-foreground transition-colors hover:text-foreground" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}</button></div>
          </FormField>
          <Button type="submit" variant="accent" size="lg" className="w-full" disabled={pending}>{pending && <LoaderCircle aria-hidden="true" className="animate-spin" />} {pending ? "Signing in…" : "Sign in"}</Button>
          <div className="flex flex-col gap-2 text-center text-sm sm:flex-row sm:justify-between"><Link className="rounded-sm text-primary underline-offset-4 hover:underline" href="/forgot-password">Forgot password?</Link><Link className="rounded-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline" href="/verify-email">Activate an account</Link></div>
        </form>
      ) : (
        <form className="mt-7 space-y-5" onSubmit={submitMfa} noValidate>
          {challenge?.state === "MFA_ENROLLMENT_REQUIRED" && <Alert><div><strong>Authenticator setup</strong><p className="mt-1">Add this key to your authenticator app, then enter its current code.</p><code className="mt-3 block break-all rounded bg-background px-3 py-2 font-mono text-xs select-all">{challenge.enrollmentSecret}</code></div></Alert>}
          <FormField label="Verification or recovery code" htmlFor="code" error={errors.code}><Input id="code" inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(event) => setCode(event.target.value)} aria-invalid={Boolean(errors.code)} aria-describedby={errors.code ? "code-error" : undefined} /></FormField>
          <Button type="submit" variant="accent" size="lg" className="w-full" disabled={pending}>{pending && <LoaderCircle aria-hidden="true" className="animate-spin" />} {pending ? "Verifying…" : "Verify and continue"}</Button>
          <Button variant="ghost" className="w-full" onClick={() => { setStage("login"); setChallenge(undefined); setCode(""); }}>Back to sign in</Button>
        </form>
      )}
    </div>
  );
}
