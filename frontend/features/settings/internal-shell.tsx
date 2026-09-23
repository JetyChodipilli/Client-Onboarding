"use client";

import { BriefcaseBusiness, Building2, GitBranch, KeyRound, LayoutDashboard, LogOut, Network, ScrollText, Settings2, Users } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { createContext, useContext, useEffect, useState } from "react";
import { ThemeToggle } from "@/components/shared/theme-toggle";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { authApi } from "@/features/auth/auth-api";
import { useSignOut } from "@/features/auth/use-sign-out";
import type { AuthUser } from "@/features/auth/types";
import { cn } from "@/lib/utils";

const UserContext = createContext<AuthUser | null>(null);
export const useCurrentUser = () => { const value = useContext(UserContext); if (!value) throw new Error("User context is unavailable"); return value; };

type NavItem = { href: string; label: string; icon: typeof LayoutDashboard; permission?: string; permissions?: string[] };

const nav: NavItem[] = [
  { href: "/app", label: "Overview", icon: LayoutDashboard },
  { href: "/app/clients", label: "Clients", icon: BriefcaseBusiness, permissions: ["CLIENT_READ"] },
  { href: "/app/projects", label: "Projects", icon: GitBranch, permissions: ["PROJECT_READ"] },
  { href: "/app/services", label: "Services", icon: Settings2, permissions: ["SERVICE_MANAGE", "PROJECT_CREATE", "WORKFLOW_MANAGE"] },
  { href: "/app/workflows", label: "Workflows", icon: Network, permissions: ["WORKFLOW_READ"] },
  { href: "/app/settings/organization", label: "Organization", icon: Building2, permission: "ORGANIZATION_READ" },
  { href: "/app/settings/members", label: "Members", icon: Users, permission: "USER_MANAGE" },
  { href: "/app/settings/roles", label: "Roles", icon: KeyRound, permission: "ROLE_MANAGE" },
  { href: "/app/settings/audit", label: "Audit log", icon: ScrollText, permission: "AUDIT_READ" },
];

export function InternalShell({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [error, setError] = useState("");
  const pathname = usePathname();
  const { signOut, pending, error: logoutError } = useSignOut("/login");
  useEffect(() => { authApi.me().then(setUser).catch(() => setError("Your session is unavailable or expired.")); }, []);

  if (error) return <main className="page-shell grid min-h-dvh place-items-center py-16"><div className="max-w-lg"><Alert tone="error">{error}</Alert><Link href="/login" className="mt-5 inline-flex min-h-11 items-center font-semibold text-primary hover:underline">Return to sign in</Link></div></main>;
  if (!user) return <main className="min-h-dvh lg:grid lg:grid-cols-[17rem_minmax(0,1fr)]" aria-busy="true" aria-label="Loading secure workspace"><aside className="hidden border-r bg-card/70 p-5 lg:block"><div className="h-10 w-44 animate-pulse rounded-md bg-muted" /><div className="mt-10 space-y-3">{Array.from({ length: 5 }, (_, index) => <div key={index} className="h-11 animate-pulse rounded-md bg-muted" />)}</div></aside><div className="p-5 sm:p-8 lg:p-10"><p className="text-sm text-muted-foreground">Loading your secure workspace…</p><div className="mt-6 h-10 w-2/3 max-w-xl animate-pulse rounded-md bg-muted" /><div className="mt-10 grid gap-4 md:grid-cols-3">{Array.from({ length: 3 }, (_, index) => <div key={index} className="h-52 animate-pulse rounded-xl bg-muted" />)}</div></div></main>;
  const items = nav.filter((item) => (!item.permission || user.permissions.includes(item.permission))
    && (!item.permissions || item.permissions.some((permission) => user.permissions.includes(permission))));
  return (
    <UserContext.Provider value={user}>
      <a href="#workspace-main" className="fixed left-3 top-3 z-50 -translate-y-24 rounded-md bg-foreground px-4 py-3 text-sm font-semibold text-background transition-transform focus:translate-y-0">Skip to workspace</a>
      <div className="min-h-dvh bg-background lg:grid lg:grid-cols-[17rem_minmax(0,1fr)]">
        <aside className="border-b bg-card/80 lg:sticky lg:top-0 lg:h-dvh lg:border-b-0 lg:border-r">
          <div className="flex min-h-20 items-center justify-between gap-3 px-5 lg:border-b">
            <Link href="/app" className="inline-flex min-h-11 items-center gap-3 rounded-md font-semibold"><span className="grid size-9 place-items-center rounded-lg bg-primary text-primary-foreground"><Network aria-hidden="true" className="size-4" /></span><span>Client Onboarding</span></Link>
            <div className="flex items-center gap-1 lg:hidden"><ThemeToggle /><Button size="icon" variant="ghost" aria-label="Sign out" onClick={signOut} disabled={pending}><LogOut aria-hidden="true" /></Button></div>
          </div>
          <nav aria-label="Workspace" className="flex gap-1 overflow-x-auto px-3 pb-3 lg:block lg:space-y-1 lg:overflow-visible lg:py-5">
            {items.map(({ href, label, icon: Icon }) => { const active = pathname === href || href !== "/app" && pathname.startsWith(`${href}/`); return <Link key={href} href={href} aria-current={active ? "page" : undefined} className={cn("inline-flex min-h-11 shrink-0 cursor-pointer items-center gap-3 rounded-md px-3.5 text-sm font-medium transition-colors", active ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground")}><Icon aria-hidden="true" className="size-4" />{label}</Link>; })}
          </nav>
          <div className="hidden border-t p-4 lg:absolute lg:inset-x-0 lg:bottom-0 lg:block">
            <p className="truncate text-sm font-semibold">{user.displayName}</p><p className="truncate text-xs text-muted-foreground">{user.role}</p>
            <div className="mt-3 flex items-center justify-between"><ThemeToggle /><Button size="sm" variant="ghost" onClick={signOut} disabled={pending}><LogOut aria-hidden="true" />{pending ? "Signing out…" : "Sign out"}</Button></div>
          </div>
        </aside>
        <div className="min-w-0"><header className="hidden min-h-20 items-center justify-between border-b bg-card/50 px-8 lg:flex"><div><p className="text-sm font-semibold">{user.displayName}</p><p className="text-xs text-muted-foreground">{user.email}</p></div><span className="rounded-full border bg-muted px-3 py-1 text-xs font-semibold">{user.role}</span></header><main id="workspace-main" tabIndex={-1} className="mx-auto w-full max-w-6xl p-5 sm:p-8 lg:p-10">{logoutError && <Alert tone="error" className="mb-6">{logoutError}</Alert>}{children}</main></div>
      </div>
    </UserContext.Provider>
  );
}
