"use client";
import Link from "next/link";
import { ArrowUpRight, BriefcaseBusiness, Fingerprint, FolderKanban, KeyRound, ShieldCheck, Users, UserRoundSearch, ChartNoAxesCombined } from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { PageHeading } from "@/components/layout/page-heading";
import { Badge } from "@/components/ui/badge";

export default function WorkspaceOverview(){
  const{user,hasPermission}=useAuth();if(!user)return null;
  const controls=[{title:"Tenant boundary",body:"Every protected request resolves your active organization membership before permissions are evaluated.",icon:Fingerprint},{title:"Live permissions",body:`${user.permissions.length} effective permissions are evaluated from active role mappings.`,icon:KeyRound},{title:"Session protection",body:"Refresh tokens rotate, security-sensitive changes invalidate sessions, and privileged access requires MFA.",icon:ShieldCheck}];
  const work=[
    hasPermission("CLIENT_READ")?{href:"/app/clients",title:"Clients",body:"Companies, relationship status, and distinct client contacts.",icon:UserRoundSearch}:null,
    hasPermission("PROJECT_READ")?{href:"/app/projects",title:"Projects",body:"Client-service engagements, project teams, lifecycle, and activity.",icon:FolderKanban}:null,
    (hasPermission("SERVICE_READ")||hasPermission("SERVICE_MANAGE"))?{href:"/app/services",title:"Service catalog",body:"Reusable service types available when projects are created.",icon:BriefcaseBusiness}:null,
    hasPermission("REPORT_READ")?{href:"/app/reports",title:"Reports & analytics",body:"Operational, funnel, financial, contract, and duration signals.",icon:ChartNoAxesCombined}:null,
    hasPermission("USER_READ")?{href:"/app/users",title:"People & memberships",body:"Tenant users, access status, and assigned roles.",icon:Users}:null,
  ].filter(Boolean) as Array<{href:string;title:string;body:string;icon:typeof Users}>;
  return <>
    <PageHeading eyebrow="Operations workspace" title={`Good morning, ${user.displayName.split(" ")[0]}.`} description="Identity controls now protect the client and project core. Business records remain tenant-scoped, permission-gated, and auditable."/>
    <div className="grid gap-4 md:grid-cols-3">{controls.map(({title,body,icon:Icon})=><article key={title} className="panel p-5"><div className="mb-7 flex items-center justify-between"><div className="grid size-10 place-items-center rounded-xl bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]"><Icon className="size-5"/></div><Badge variant="success">Active</Badge></div><h2 className="text-base font-semibold">{title}</h2><p className="mt-2 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{body}</p></article>)}</div>
    <div className="mt-6 grid gap-6 lg:grid-cols-[1.35fr_.65fr]"><section className="panel overflow-hidden"><div className="border-b px-5 py-4"><h2 className="font-semibold">Your workspace</h2><p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Only destinations authorized by your current permissions are shown.</p></div>{work.length?<div className="divide-y">{work.map(item=><Quick key={item.href} {...item}/>)}</div>:<p className="px-5 py-8 text-sm text-[hsl(var(--muted-foreground))]">No operational modules are assigned to your current permission set.</p>}</section><section className="rounded-2xl bg-[hsl(var(--ink))] p-6 text-white shadow-[var(--shadow-card)]"><p className="text-xs font-bold uppercase tracking-[.16em] text-white/50">Workspace</p><p className="mt-3 text-xl font-semibold">{user.organizationName}</p><p className="mt-1 text-sm text-white/55">{user.organizationSlug}</p><div className="mt-8 border-t border-white/10 pt-5"><p className="text-xs text-white/45">Signed in as</p><p className="mt-1 truncate text-sm font-medium">{user.email}</p></div></section></div>
  </>
}
function Quick({href,title,body,icon:Icon}:{href:string;title:string;body:string;icon:typeof Users}){return <Link href={href} className="group flex items-center gap-4 px-5 py-4 hover:bg-[hsl(var(--surface-subtle))]"><div className="grid size-9 place-items-center rounded-lg border bg-white"><Icon className="size-4"/></div><div className="min-w-0 flex-1"><p className="text-sm font-semibold">{title}</p><p className="mt-0.5 text-xs text-[hsl(var(--muted-foreground))]">{body}</p></div><ArrowUpRight className="size-4 text-[hsl(var(--muted-foreground))] transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5"/></Link>}
