"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { KeyRound, Loader2, Pencil, Plus, ShieldCheck } from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { FormMessage } from "@/components/auth/form-message";
import { PageHeading } from "@/components/layout/page-heading";
import { EmptyState } from "@/components/shared/empty-state";
import { PermissionState } from "@/components/shared/permission-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiClientError } from "@/services/api-client";
import type { Permission, Role } from "@/types/organization";

export default function RolesPage() {
  const { authorizedRequest, hasPermission, user } = useAuth();
  const canRead = hasPermission("ROLE_READ") || hasPermission("ROLE_MANAGE");
  const canManage = hasPermission("ROLE_MANAGE");
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const [roleRows, permissionRows] = await Promise.all([
        authorizedRequest<Role[]>("/roles"),
        authorizedRequest<Permission[]>("/permissions"),
      ]);
      setRoles(roleRows); setPermissions(permissionRows);
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "Roles could not be loaded.");
    } finally { setLoading(false); }
  }, [authorizedRequest]);

  useEffect(() => { if (canRead) void load(); }, [canRead, load]);
  if (!canRead) return <PermissionState />;

  const heldPermissionCodes = user?.permissions ?? [];

  return (
    <>
      <PageHeading
        eyebrow="Authorization"
        title="Roles & permissions"
        description="Tenant-scoped permission bundles with delegation guardrails and immutable system roles."
        actions={canManage ? <Button onClick={() => { setCreating((value) => !value); setEditing(null); }}><Plus className="size-4" />New role</Button> : undefined}
      />
      {creating && <RoleEditor permissions={permissions} heldPermissionCodes={heldPermissionCodes} onDone={() => { setCreating(false); void load(); }} />}
      {error && <div className="mb-5"><FormMessage>{error}</FormMessage></div>}
      {loading ? (
        <div className="panel flex justify-center py-16 text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin" />Loading authorization model…</div>
      ) : roles.length === 0 ? (
        <div className="panel"><EmptyState title="No active roles" description="Create a role from permissions you are authorized to delegate." /></div>
      ) : (
        <div className="grid gap-4 xl:grid-cols-2">
          {roles.map((role) => (
            <div key={role.id} className="space-y-3">
              <article className="panel p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex gap-3">
                    <div className="grid size-10 shrink-0 place-items-center rounded-xl bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]">{role.systemRole ? <ShieldCheck className="size-5" /> : <KeyRound className="size-5" />}</div>
                    <div><div className="flex flex-wrap items-center gap-2"><h2 className="font-semibold">{role.name}</h2>{role.systemRole && <Badge variant="info">System</Badge>}</div><p className="mt-1 font-mono text-xs text-[hsl(var(--muted-foreground))]">{role.code}</p></div>
                  </div>
                  <div className="flex items-center gap-2"><Badge>{role.permissions.length} permissions</Badge>{canManage && !role.systemRole && <Button variant="ghost" size="icon" aria-label={`Edit ${role.name}`} onClick={() => { setEditing((current) => current === role.id ? null : role.id); setCreating(false); }}><Pencil className="size-4" /></Button>}</div>
                </div>
                <p className="mt-4 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{role.description || "No description provided."}</p>
                <div className="mt-5 flex flex-wrap gap-1.5">{role.permissions.slice(0, 10).map((permission) => <span key={permission} className="rounded-md bg-[hsl(var(--surface-subtle))] px-2 py-1 font-mono text-[11px] font-medium">{permission}</span>)}{role.permissions.length > 10 && <span className="rounded-md bg-[hsl(var(--surface-subtle))] px-2 py-1 text-[11px]">+{role.permissions.length - 10} more</span>}</div>
                {role.systemRole && <p className="mt-5 border-t pt-4 text-xs leading-5 text-[hsl(var(--muted-foreground))]">System roles are immutable to preserve the administrative recovery boundary.</p>}
              </article>
              {editing === role.id && <RoleEditor role={role} permissions={permissions} heldPermissionCodes={heldPermissionCodes} onDone={() => { setEditing(null); void load(); }} />}
            </div>
          ))}
        </div>
      )}
    </>
  );
}

function RoleEditor({ role, permissions, heldPermissionCodes, onDone }: { role?: Role; permissions: Permission[]; heldPermissionCodes: string[]; onDone: () => void }) {
  const { authorizedRequest } = useAuth();
  const [name, setName] = useState(role?.name ?? "");
  const [code, setCode] = useState(role?.code ?? "");
  const [description, setDescription] = useState(role?.description ?? "");
  const [selected, setSelected] = useState<string[]>(() => permissions.filter((permission) => role?.permissions.includes(permission.code)).map((permission) => permission.id));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const groups = useMemo(() => permissions.reduce((acc, permission) => { const items = acc.get(permission.category) ?? []; items.push(permission); acc.set(permission.category, items); return acc; }, new Map<string, Permission[]>()), [permissions]);

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      if (role) {
        await authorizedRequest(`/roles/${role.id}`, { method: "PATCH", body: JSON.stringify({ name, description, permissionIds: selected, version: role.version }) });
      } else {
        await authorizedRequest("/roles", { method: "POST", body: JSON.stringify({ name, code, description, permissionIds: selected }) });
      }
      onDone();
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "Role could not be saved.");
    } finally { setBusy(false); }
  }

  return (
    <section className="panel p-5 sm:p-6">
      <h2 className="font-semibold">{role ? `Edit ${role.name}` : "Create a custom role"}</h2>
      <p className="mt-1 text-sm text-[hsl(var(--muted-foreground))]">You may retain or remove permissions already on this role. New permissions can be added only when you currently hold them. Removing the last recovery administrator is blocked by the server.</p>
      <form onSubmit={submit} className="mt-5 grid gap-4 sm:grid-cols-2">
        <div className="space-y-2"><Label htmlFor={`${role?.id ?? "new"}-role-name`}>Role name</Label><Input id={`${role?.id ?? "new"}-role-name`} value={name} onChange={(event) => setName(event.target.value)} required /></div>
        <div className="space-y-2"><Label htmlFor={`${role?.id ?? "new"}-role-code`}>Role code</Label><Input id={`${role?.id ?? "new"}-role-code`} value={code} onChange={(event) => setCode(event.target.value)} placeholder="ACCOUNT_MANAGER" disabled={Boolean(role)} required /></div>
        <div className="space-y-2 sm:col-span-2"><Label htmlFor={`${role?.id ?? "new"}-role-description`}>Description</Label><Input id={`${role?.id ?? "new"}-role-description`} value={description} onChange={(event) => setDescription(event.target.value)} /></div>
        <div className="sm:col-span-2"><p className="mb-3 text-sm font-medium">Permissions</p><div className="space-y-4">{Array.from(groups.entries()).map(([category, items]) => <fieldset key={category}><legend className="mb-2 text-xs font-bold uppercase tracking-[.12em] text-[hsl(var(--muted-foreground))]">{category}</legend><div className="grid gap-2 lg:grid-cols-2">{items.map((permission) => {
              const isSelected = selected.includes(permission.id);
              const canAdd = heldPermissionCodes.includes(permission.code);
              const disabled = !isSelected && !canAdd;
              return (
                <label key={permission.id} className={`flex items-start gap-3 rounded-xl border p-3 ${disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer hover:bg-[hsl(var(--surface-subtle))]"}`}>
                  <input type="checkbox" className="mt-1" checked={isSelected} disabled={disabled} onChange={(event) => setSelected((current) => event.target.checked ? [...current, permission.id] : current.filter((id) => id !== permission.id))} />
                  <span>
                    <span className="flex flex-wrap items-center gap-2"><span className="font-mono text-xs font-semibold">{permission.code}</span>{disabled && <span className="text-[10px] font-semibold uppercase tracking-wide text-[hsl(var(--muted-foreground))]">Cannot add</span>}</span>
                    <span className="mt-1 block text-xs leading-5 text-[hsl(var(--muted-foreground))]">{permission.description}</span>
                  </span>
                </label>
              );
            })}</div></fieldset>)}</div></div>
        {error && <div className="sm:col-span-2"><FormMessage>{error}</FormMessage></div>}
        <div className="flex justify-end sm:col-span-2"><Button disabled={busy || selected.length === 0}>{busy && <Loader2 className="size-4 animate-spin" />}{role ? "Save role" : "Create role"}</Button></div>
      </form>
    </section>
  );
}
