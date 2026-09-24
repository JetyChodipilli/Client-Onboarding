"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { formsApi } from "./forms-api";
import { formControl } from "./question-fields";
import type { FormTemplate } from "./types";

export function FormsWorkspace() {
  const user = useCurrentUser(); const canManage = user.permissions.includes("FORM_MANAGE");
  const allowed = canManage || user.permissions.includes("FORM_READ");
  const [rows, setRows] = useState<FormTemplate[]>(); const [search, setSearch] = useState(""); const [page, setPage] = useState(0);
  const [error, setError] = useState(""); const [pending, setPending] = useState(false); const [name, setName] = useState(""); const [description, setDescription] = useState("");
  const [created, setCreated] = useState<FormTemplate>();
  useEffect(() => { if (!allowed) return; let active = true; const timeout = setTimeout(() => {
    formsApi.list(search, page).then((values) => { if (active) { setRows(values); setError(""); } }).catch((e) => { if (active) setError(e.message); });
  }, 250); return () => { active = false; clearTimeout(timeout); }; }, [search, page, allowed, created]);
  async function create(e: React.FormEvent) { e.preventDefault(); setPending(true); setError(""); try { const value = await formsApi.create(name, description); setCreated(value.template); setName(""); setDescription(""); } catch (e) { setError(e instanceof Error ? e.message : "The form could not be created."); } finally { setPending(false); } }
  if (!allowed) return <Alert>You do not have permission to read form templates.</Alert>;
  return <><p className="text-sm font-bold text-primary">Reusable questionnaires</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">Forms</h1><p className="mt-3 max-w-2xl text-muted-foreground">Build a questionnaire, publish a version, and attach it to a workflow. Existing projects keep the questions they started with.</p>
    {error && <Alert tone="error" className="mt-5">{error}</Alert>}{created && <Alert tone="success" className="mt-5">Form created. <Link className="font-semibold underline" href={`/app/forms/${created.id}`}>Build {created.name}</Link></Alert>}
    <div className="mt-8 grid gap-6 lg:grid-cols-12"><section className="min-w-0 lg:col-span-7" aria-label="Form templates"><label htmlFor="form-search" className="text-sm font-semibold">Search forms</label><Input id="form-search" className="mt-2" maxLength={180} value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
      {!rows ? <p className="mt-5" aria-busy="true">Loading forms…</p> : !rows.length ? <Card className="mt-5"><h2 className="font-bold">No forms found</h2><p className="mt-2 text-sm text-muted-foreground">{search ? "Try a different search." : "Create your first questionnaire to collect project requirements."}</p></Card> : <ul className="mt-5 space-y-3">{rows.map((row) => <li key={row.id}><Link href={`/app/forms/${row.id}`} className="block rounded-xl border bg-card p-5 transition-colors hover:bg-muted"><h2 className="font-bold [overflow-wrap:anywhere]">{row.name}</h2><p className="mt-2 text-sm text-muted-foreground [overflow-wrap:anywhere]">{row.description || "Open fields and versions"}</p></Link></li>)}</ul>}
      <div className="mt-5 flex gap-3"><Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><Button variant="outline" disabled={!rows || rows.length < 20} onClick={() => setPage(page + 1)}>Next</Button></div></section>
      {canManage && <Card className="h-fit lg:col-span-5"><h2 className="text-xl font-bold">Create a form</h2><form className="mt-5 space-y-4" onSubmit={create}><label className="block text-sm font-semibold">Form name<Input className="mt-2" required maxLength={180} value={name} onChange={(e) => setName(e.target.value)} /></label><label className="block text-sm font-semibold">Description<textarea className={`${formControl} min-h-24`} maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} /></label><Button type="submit" disabled={pending}>{pending ? "Creating…" : "Create form"}</Button></form></Card>}</div></>;
}
