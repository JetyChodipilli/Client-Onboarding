"use client";

import Link from "next/link";
import { Building2, KeyRound, Mail, ShieldCheck, UserRound } from "lucide-react";
import { useClientAuth } from "@/auth/client-auth-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

export default function ClientProfilePage() {
  const { user } = useClientAuth();
  if (!user) return null;

  return <div className="page-enter">
    <header className="mb-8"><p className="text-xs font-bold uppercase tracking-[.16em] text-[hsl(var(--primary))]">Profile</p><h1 className="mt-2 text-3xl font-semibold tracking-[-.045em]">Your secure workspace identity</h1><p className="mt-3 max-w-2xl text-sm leading-6 text-[hsl(var(--muted-foreground))]">Review the identity used for this client workspace. Project access is granted separately and remains limited to projects your organization has shared with you.</p></header>

    <div className="grid gap-5 lg:grid-cols-[1.1fr_.9fr]">
      <section className="panel overflow-hidden">
        <div className="border-b bg-[hsl(var(--surface-subtle)/.55)] p-5 sm:p-6"><div className="flex items-center gap-3"><div className="grid size-11 place-items-center rounded-2xl bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]"><UserRound className="size-5"/></div><div><h2 className="font-semibold">Account details</h2><p className="mt-0.5 text-xs text-[hsl(var(--muted-foreground))]">Authenticated client identity</p></div></div></div>
        <dl className="divide-y">
          <Row icon={UserRound} label="Name" value={user.displayName}/>
          <Row icon={Mail} label="Email" value={user.email}/>
          <Row icon={Building2} label="Organization" value={user.organizationName}/>
          <div className="flex items-center justify-between gap-4 px-5 py-4 sm:px-6"><dt className="text-sm text-[hsl(var(--muted-foreground))]">Access scope</dt><dd><Badge variant="success">Client portal</Badge></dd></div>
        </dl>
      </section>

      <section className="panel h-fit p-5 sm:p-6">
        <div className="flex items-start gap-3"><div className="grid size-10 shrink-0 place-items-center rounded-xl bg-[hsl(var(--success)/.1)] text-[hsl(var(--success))]"><ShieldCheck className="size-5"/></div><div><h2 className="font-semibold">Security</h2><p className="mt-1 text-sm leading-6 text-[hsl(var(--muted-foreground))]">Your access token is short-lived and the refresh credential is kept in a protected HttpOnly cookie. Passwords for third-party platforms should never be entered into this onboarding portal.</p></div></div>
        <div className="mt-5 border-t pt-5"><Button asChild variant="secondary"><Link href="/portal/forgot-password"><KeyRound className="size-4"/>Reset password</Link></Button><p className="mt-2 text-xs leading-5 text-[hsl(var(--muted-foreground))]">Password reset invalidates existing security-sensitive sessions after the credential change.</p></div>
      </section>
    </div>
  </div>;
}

function Row({ icon: Icon, label, value }: { icon: typeof UserRound; label: string; value: string }) {
  return <div className="grid grid-cols-[auto_1fr] items-center gap-x-3 px-5 py-4 sm:grid-cols-[1fr_1.4fr] sm:px-6"><dt className="flex items-center gap-2 text-sm text-[hsl(var(--muted-foreground))]"><Icon className="size-4"/>{label}</dt><dd className="min-w-0 truncate text-right text-sm font-medium sm:text-left">{value}</dd></div>;
}
