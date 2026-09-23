"use client";

import { LogOut, Network } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ThemeToggle } from "@/components/shared/theme-toggle";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { authApi } from "@/features/auth/auth-api";
import type { AuthUser } from "@/features/auth/types";

export function PortalShell({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser>(); const [error, setError] = useState(""); const router = useRouter();
  useEffect(() => { authApi.me().then((value) => value.permissions.includes("CLIENT_PORTAL_READ") ? setUser(value) : setError("This account does not have client portal access.")).catch(() => setError("Your client session is unavailable or expired.")); }, []);
  async function logout() { await authApi.logout().catch(() => undefined); router.push("/client/login"); }
  if (error) return <main className="page-shell grid min-h-dvh place-items-center py-16"><div className="max-w-lg"><Alert tone="error">{error}</Alert><Link href="/client/login" className="mt-5 inline-flex min-h-11 items-center font-semibold text-primary hover:underline">Return to client sign in</Link></div></main>;
  if (!user) return <main className="page-shell min-h-dvh py-8" aria-busy="true"><div className="h-16 animate-pulse rounded-xl bg-muted" /><div className="mt-8 h-72 animate-pulse rounded-xl bg-muted" /></main>;
  return <><a href="#portal-main" className="fixed left-3 top-3 z-50 -translate-y-24 rounded-md bg-foreground px-4 py-3 text-sm font-semibold text-background transition-transform focus:translate-y-0">Skip to portal</a><header className="border-b bg-card/80 backdrop-blur"><div className="mx-auto flex min-h-20 max-w-6xl items-center justify-between gap-4 px-5 sm:px-8"><Link href="/portal" className="inline-flex min-h-11 items-center gap-3 rounded-md font-semibold"><span className="grid size-9 place-items-center rounded-lg bg-primary text-primary-foreground"><Network aria-hidden="true" className="size-4" /></span><span><span className="block leading-tight">Client Onboarding</span><span className="block text-xs font-medium text-muted-foreground">{user.organizationName}</span></span></Link><div className="flex items-center gap-1"><ThemeToggle /><Button size="sm" variant="ghost" className="shrink-0 whitespace-nowrap" onClick={logout}><LogOut aria-hidden="true" />Sign out</Button></div></div></header><main id="portal-main" tabIndex={-1} className="mx-auto w-full max-w-6xl p-5 [overflow-wrap:anywhere] sm:p-8 lg:p-10">{children}</main></>;
}
