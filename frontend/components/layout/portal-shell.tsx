"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { ArrowRight, Bell, CheckSquare2, LayoutDashboard, LogOut, ShieldCheck, UserRound } from "lucide-react";
import { useClientAuth } from "@/auth/client-auth-provider";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const portalNav = [
  { href: "/portal", label: "Overview", icon: LayoutDashboard },
  { href: "/portal/tasks", label: "Tasks", icon: CheckSquare2 },
  { href: "/portal/notifications", label: "Updates", icon: Bell },
  { href: "/portal/profile", label: "Profile", icon: UserRound },
] as const;

export function PortalShell({ children }: { children: React.ReactNode }) {
  const { status, user, logout } = useClientAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") router.replace(`/portal/login?next=${encodeURIComponent(pathname)}`);
  }, [status, router, pathname]);

  if (status === "loading") {
    return <div className="grid min-h-screen place-items-center bg-[hsl(var(--background))]"><div className="flex items-center gap-3 text-sm text-[hsl(var(--muted-foreground))]"><span className="size-2 animate-pulse rounded-full bg-[hsl(var(--primary))]"/>Opening your secure workspace…</div></div>;
  }
  if (status !== "authenticated" || !user) return null;

  return <div className="min-h-screen bg-[hsl(var(--background))]">
    <header className="sticky top-0 z-40 border-b bg-[hsl(var(--surface)/.94)] backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-[86rem] items-center justify-between gap-4 px-4 sm:px-6 lg:h-18 lg:px-8">
        <div className="flex min-w-0 items-center gap-3"><Link href="/portal" className="grid size-9 shrink-0 place-items-center rounded-xl bg-[hsl(var(--ink))] text-xs font-black text-white shadow-sm">CO</Link><div className="min-w-0"><p className="truncate text-sm font-semibold">{user.organizationName}</p><p className="truncate text-[11px] text-[hsl(var(--muted-foreground))]">Client workspace</p></div></div>
        <nav className="hidden items-center gap-1 sm:flex" aria-label="Client workspace navigation">{portalNav.map(item => <PortalNav key={item.href} {...item} pathname={pathname}/>)}</nav>
        <div className="flex items-center gap-2"><div className="hidden text-right md:block"><p className="max-w-48 truncate text-sm font-medium">{user.displayName}</p><p className="max-w-48 truncate text-[11px] text-[hsl(var(--muted-foreground))]">{user.email}</p></div><Button variant="ghost" size="icon" aria-label="Sign out" onClick={async () => { await logout(); router.replace("/portal/login"); }}><LogOut className="size-4"/></Button></div>
      </div>
    </header>

    <div className="border-b bg-[hsl(var(--surface))]"><div className="mx-auto flex max-w-[86rem] items-center gap-2 px-4 py-2.5 text-xs text-[hsl(var(--muted-foreground))] sm:px-6 lg:px-8"><ShieldCheck className="size-3.5 text-[hsl(var(--success))]"/><span>Project-scoped secure access</span><ArrowRight className="size-3"/><span className="truncate">Your team controls what is visible here.</span></div></div>

    <main className="mobile-safe-bottom mx-auto w-full max-w-[86rem] px-4 py-7 sm:px-6 sm:py-9 lg:px-8 lg:py-11">{children}</main>

    <nav className="fixed inset-x-0 bottom-0 z-40 border-t bg-[hsl(var(--surface)/.97)] px-2 pb-[calc(.5rem+env(safe-area-inset-bottom))] pt-2 backdrop-blur-xl sm:hidden" aria-label="Client workspace mobile navigation">
      <div className="mx-auto grid max-w-lg grid-cols-4 gap-1">{portalNav.map(item => <MobilePortalNav key={item.href} {...item} pathname={pathname}/>)}</div>
    </nav>
  </div>;
}

function PortalNav({ href, label, icon: Icon, pathname }: { href: string; label: string; icon: typeof LayoutDashboard; pathname: string }) {
  const active = href === "/portal" ? pathname === href : pathname.startsWith(href);
  return <Link href={href} aria-current={active ? "page" : undefined} className={cn("inline-flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-medium transition-colors", active ? "bg-[hsl(var(--surface-subtle))] text-[hsl(var(--foreground))]" : "text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))]")}><Icon className="size-4"/>{label}</Link>;
}

function MobilePortalNav({ href, label, icon: Icon, pathname }: { href: string; label: string; icon: typeof LayoutDashboard; pathname: string }) {
  const active = href === "/portal" ? pathname === href : pathname.startsWith(href);
  return <Link href={href} aria-current={active ? "page" : undefined} className={cn("flex min-h-12 flex-col items-center justify-center gap-1 rounded-xl px-2 text-[11px] font-semibold transition-colors", active ? "bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]" : "text-[hsl(var(--muted-foreground))]")}><Icon className="size-4"/>{label}</Link>;
}
