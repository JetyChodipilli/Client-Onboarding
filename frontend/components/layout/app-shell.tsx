"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  AlarmClock, Bell, BriefcaseBusiness, Building2, ChartNoAxesCombined, CheckSquare2, ChevronDown,
  FileClock, FileSignature, FileText, FolderKanban, FolderOpen, GitBranch, KeyRound, LayoutDashboard,
  LogOut, Menu, PlugZap, ReceiptText, ShieldCheck, Users, UserRoundSearch, X,
} from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type NavItem = { href: string; label: string; icon: typeof LayoutDashboard; permissions: string[] };
type NavGroup = { label: string; items: NavItem[] };

const navGroups: NavGroup[] = [
  { label: "Workspace", items: [
    { href: "/app", label: "Overview", icon: LayoutDashboard, permissions: [] },
    { href: "/app/clients", label: "Clients", icon: UserRoundSearch, permissions: ["CLIENT_READ"] },
    { href: "/app/projects", label: "Projects", icon: FolderKanban, permissions: ["PROJECT_READ"] },
  ] },
  { label: "Build", items: [
    { href: "/app/services", label: "Services", icon: BriefcaseBusiness, permissions: ["SERVICE_READ", "SERVICE_MANAGE"] },
    { href: "/app/workflows", label: "Workflows", icon: GitBranch, permissions: ["WORKFLOW_READ", "WORKFLOW_MANAGE"] },
    { href: "/app/forms", label: "Forms", icon: FileText, permissions: ["FORM_READ", "FORM_MANAGE", "FORM_REVIEW"] },
  ] },
  { label: "Operations", items: [
    { href: "/app/assets", label: "Assets", icon: FolderOpen, permissions: ["ASSET_READ", "ASSET_REVIEW"] },
    { href: "/app/invoices", label: "Billing", icon: ReceiptText, permissions: ["INVOICE_CREATE", "INVOICE_SEND", "PAYMENT_OVERRIDE"] },
    { href: "/app/contracts", label: "Contracts", icon: FileSignature, permissions: ["CONTRACT_CREATE", "CONTRACT_SEND"] },
    { href: "/app/access", label: "Platform access", icon: KeyRound, permissions: ["ACCESS_READ", "ACCESS_MANAGE", "ACCESS_VERIFY"] },
    { href: "/app/tasks", label: "Tasks", icon: CheckSquare2, permissions: ["TASK_READ", "TASK_MANAGE"] },
  ] },
  { label: "Engagement", items: [
    { href: "/app/notifications", label: "Notifications", icon: Bell, permissions: [] },
    { href: "/app/reminders", label: "Reminders", icon: AlarmClock, permissions: ["REMINDER_MANAGE"] },
  ] },
  { label: "Insights", items: [
    { href: "/app/reports", label: "Reports", icon: ChartNoAxesCombined, permissions: ["REPORT_READ"] },
    { href: "/app/audit", label: "Audit log", icon: FileClock, permissions: ["AUDIT_READ"] },
  ] },
  { label: "Administration", items: [
    { href: "/app/users", label: "People", icon: Users, permissions: ["USER_READ", "USER_MANAGE"] },
    { href: "/app/roles", label: "Roles & permissions", icon: ShieldCheck, permissions: ["ROLE_READ", "ROLE_MANAGE"] },
    { href: "/app/integrations", label: "Integrations", icon: PlugZap, permissions: ["ORG_READ", "ORG_UPDATE"] },
    { href: "/app/settings/organization", label: "Organization", icon: Building2, permissions: ["ORG_READ", "ORG_UPDATE"] },
  ] },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const { status, user, logout, hasPermission } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  useEffect(() => { if (status === "anonymous") router.replace("/login"); }, [status, router]);
  useEffect(() => { setOpen(false); }, [pathname]);

  if (status === "loading") return <div className="grid min-h-screen place-items-center"><div className="flex items-center gap-3 text-sm text-[hsl(var(--muted-foreground))]"><span className="size-2 animate-pulse rounded-full bg-[hsl(var(--primary))]"/>Securing workspace…</div></div>;
  if (status !== "authenticated" || !user) return null;

  const visibleGroups = navGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.permissions.length === 0 || item.permissions.some(hasPermission)) }))
    .filter((group) => group.items.length > 0);

  return <div className="min-h-screen bg-[hsl(var(--background))]">
    <header className="sticky top-0 z-40 border-b bg-[hsl(var(--surface)/.92)] backdrop-blur-xl lg:hidden">
      <div className="flex h-16 items-center justify-between px-4"><div className="flex min-w-0 items-center gap-2.5"><Brand/><div className="min-w-0"><p className="truncate text-sm font-semibold leading-4">{user.organizationName}</p><p className="mt-0.5 truncate text-[11px] text-[hsl(var(--muted-foreground))]">{user.organizationSlug}</p></div></div><Button variant="ghost" size="icon" onClick={() => setOpen(!open)} aria-expanded={open} aria-controls="mobile-workspace-nav" aria-label="Toggle navigation">{open ? <X className="size-5"/> : <Menu className="size-5"/>}</Button></div>
      {open && <nav id="mobile-workspace-nav" className="max-h-[calc(100dvh-4rem)] overflow-y-auto border-t px-3 py-4" aria-label="Workspace navigation"><GroupedNavigation groups={visibleGroups} pathname={pathname}/><div className="mt-4 border-t pt-3"><Button variant="ghost" className="w-full justify-start" onClick={async () => { await logout(); router.replace("/login"); }}><LogOut className="size-4"/>Sign out</Button></div></nav>}
    </header>

    <aside className="fixed inset-y-0 left-0 z-30 hidden w-72 border-r bg-[hsl(var(--surface))] lg:flex lg:flex-col">
      <div className="flex h-20 items-center gap-3 border-b px-6"><Brand/><div className="min-w-0"><p className="truncate text-sm font-semibold">{user.organizationName}</p><p className="truncate text-xs text-[hsl(var(--muted-foreground))]">{user.organizationSlug}</p></div></div>
      <nav className="flex-1 overflow-y-auto p-4" aria-label="Workspace navigation"><GroupedNavigation groups={visibleGroups} pathname={pathname}/></nav>
      <div className="border-t p-4"><div className="mb-2 flex items-center gap-3 rounded-xl px-2 py-2"><div className="grid size-9 shrink-0 place-items-center rounded-full bg-[hsl(var(--primary-soft))] text-xs font-bold text-[hsl(var(--primary))]">{initials(user.displayName)}</div><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{user.displayName}</p><p className="truncate text-xs text-[hsl(var(--muted-foreground))]">{user.email}</p></div><ChevronDown className="size-4 text-[hsl(var(--muted-foreground))]"/></div><Button variant="ghost" className="w-full justify-start" onClick={async () => { await logout(); router.replace("/login"); }}><LogOut className="size-4"/>Sign out</Button></div>
    </aside>

    <main className="lg:pl-72"><div className="mx-auto w-full max-w-[88rem] px-5 py-7 sm:px-8 sm:py-9 lg:px-10 lg:py-10">{children}</div></main>
  </div>;
}

function GroupedNavigation({ groups, pathname }: { groups: NavGroup[]; pathname: string }) {
  return <div className="space-y-5">{groups.map((group) => <section key={group.label}><p className="nav-section-label px-3">{group.label}</p><div className="mt-1.5 space-y-1">{group.items.map((item) => <NavLink key={item.href} {...item} active={item.href === "/app" ? pathname === item.href : pathname.startsWith(item.href)}/>)}</div></section>)}</div>;
}

function NavLink({ href, label, icon: Icon, active }: NavItem & { active: boolean }) {
  return <Link href={href} aria-current={active ? "page" : undefined} className={cn("flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors", active ? "bg-[hsl(var(--ink))] text-white shadow-sm" : "text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--surface-subtle))] hover:text-[hsl(var(--foreground))]")}><Icon className="size-4.5" aria-hidden="true"/>{label}</Link>;
}

function Brand() { return <div className="grid size-10 shrink-0 place-items-center rounded-xl bg-[hsl(var(--ink))] text-sm font-black text-white shadow-sm">CO</div>; }
function initials(value: string) { return value.split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "U"; }
