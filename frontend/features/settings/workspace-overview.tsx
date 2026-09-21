"use client";
import { Building2, KeyRound, ShieldCheck, Users } from "lucide-react";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { useCurrentUser } from "./internal-shell";

export function WorkspaceOverview() {
  const user = useCurrentUser();
  const cards = [
    { title: "Organization profile", text: "Confirm the tenant name and workspace identity.", href: "/app/settings/organization", icon: Building2, permission: "ORGANIZATION_READ" },
    { title: "Team members", text: "Invite internal users and maintain access status.", href: "/app/settings/members", icon: Users, permission: "USER_MANAGE" },
    { title: "Roles & permissions", text: "Keep access explicit and permission-based.", href: "/app/settings/roles", icon: KeyRound, permission: "ROLE_MANAGE" },
  ].filter((item) => user.permissions.includes(item.permission));
  return <><div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-bold uppercase tracking-[0.15em] text-primary">Identity & access</p><h1 className="mt-3 text-3xl font-bold tracking-[-0.035em] sm:text-4xl">Workspace security baseline</h1><p className="mt-3 max-w-2xl text-muted-foreground">Your signed-in organization, role, and permissions control every protected request.</p></div><span className="inline-flex w-fit items-center gap-2 rounded-full border border-success/30 bg-success/10 px-3 py-1.5 text-sm font-semibold text-success"><ShieldCheck aria-hidden="true" className="size-4" />Session active</span></div><div className="mt-9 grid gap-4 md:grid-cols-3">{cards.map(({ title, text, href, icon: Icon }) => <Link key={href} href={href} className="group rounded-xl focus-visible:outline-none"><Card className="h-full"><Icon aria-hidden="true" className="size-6 text-primary" /><h2 className="mt-6 text-lg font-bold">{title}</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">{text}</p><span className="mt-6 inline-block text-sm font-semibold text-primary group-hover:underline">Open settings</span></Card></Link>)}</div>{cards.length === 0 && <Card className="mt-9"><h2 className="font-semibold">No administrative settings assigned</h2><p className="mt-2 text-sm text-muted-foreground">Ask an organization administrator if you need additional access.</p></Card>}</>;
}
